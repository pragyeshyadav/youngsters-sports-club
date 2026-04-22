# agents.md

## Overview
Youngsters Sports Club (YSC) is a full-stack platform designed to handle the core operations of a snooker club. It integrates table management, live game tracking (frames), partial and full payment settlements, user feedback, and role-based permissions (Customers, Managers, Admins). 

This `agents.md` serves as the ultimate source of truth for AI agents or developers navigating the codebase.

---

## Architecture

**1. Frontend (Angular)**
* **Structure**: Modern component-based architecture organized into `core`, `shared`, and `features`.
* **State Management & Logic**: Heavy lifting is handled by Angular Services built around reactive principles (`rxjs`).
* **Communication**: HTTP interceptors attach authorization (JWTs) and handle common error flows.

**2. Backend (Spring Boot)**
* **Structure**: Layered architecture -> `api` (Controllers) -> `service` (Business Logic) -> `repository` (Data Access) -> `entity` (Database Mapping).
* **Security**: Google OAuth handles the initial login `/api/auth/google-login`, and issues a stateless JWT for subsequent requests.

**3. Database**
* **Provider**: PostgreSQL (hosted on Supabase).
* **ORM**: Hibernate/JPA.
* **Migrations/Schema**: Entities tightly mapped via standard JPA annotations (`@Entity`).

**4. Deployment**
* The application runs on **Render**. Environment variables control secrets, database URIs, and Google API keys.

---

## Features

### 1. User Login (Google OAuth)
* **Purpose**: frictionless onboarding.
* **Flow**: User clicks Google Sign-In -> Google returns user payload -> Angular calls `/api/auth/google-login` -> Backend registers/fetches `User` and returns JWT.

### 2. Phone Number Update
* **Purpose**: Essential for club communication.
* **Flow**: Angular prompts users lacking a phone number. Backend `/api/user/phone` updates the user entity. It prevents overriding an already set phone number.

### 3. Dashboard
* **Purpose**: Landing experience post-login.
* **Flow**: Fetches currently active frames `getActiveFrame`, pending dues `getTotalDue`, and recent history. Highlights actionable items.

### 4. Snooker Frame Lifecycle
* **Start Frame**: User selects a table. Auto-suggest API `/api/users/search` populates player names. Backend creates `Frame` and `FramePlayer` entries. Table marked as unavailable.
* **Track Timer**: Application calculates running duration based on `startTime`.
* **End Frame**: Manager/User specifies `winner` and `loser`. Backend calculates `duration * ratePerMinute = totalAmount`. Status updates to `ENDED`, table is freed, the debt is loaded onto the loser.

### 5. Table Availability Management
* **Purpose**: Prevent double booking. 
* **Flow**: `/api/snooker/tables` returns tables where `isAvailable = true`. Started frames lock the table (`isAvailable = false`), and ended/rejected frames unlock it.

### 6. Payment & Due Tracking (Partial Payments)
* **Purpose**: Settlement of loser dues.
* **Flow**: `/api/payment/settle` accepts an amount. Backend fetches all unpaid frames for the user and iteratively subtracts the payment until depleted. Supports `PARTIAL` and `PAID` statuses.

### 7. Manager & Admin Portals
* **Purpose**: Oversight of operations.
* **Flow**: Dedicated Angular feature modules retrieve today’s ongoing frames, completed frames, and manipulate table statuses. Can reject frames. Also includes a paginated overview of all players with their aggregated activity (Total Frames Played, Total Due).

### 8. Geo-Fencing Restriction
* **Purpose**: Ensuring players are physically at the club to start a match.
* **Flow**: Handled via browser geolocation API interacting with the frontend to enforce a vicinity boundary before the 'Start Frame' POST request can fire.

### 9. Feedback System
* **Purpose**: User reviews.
* **Flow**: Simple `CustomerFeedback` model accepting a star rating (1-5) and text payload.

### 10. Kids Play Module (Ocean Dream Land)
* **Purpose**: Manage standalone children's real-time play sessions.
* **Flow**: Distinct from table management; tracks a `Child` linked to a `User` (Customer). Manager dashboards overlay specific "Parent Contexts" to initiate sessions dynamically without disrupting the user token context.

### 11. Tournament Registration (Summer Olympics)
* **Purpose**: Gamified event subscriptions and bracket structures.
* **Flow**: Renders an engaging dashboard UI for Customers. Handles deduplication gracefully via a parsed `{ successfullyRegistered, alreadyRegistered }` JSON payload feeding back into a styled success modal rather than generic exceptions.

---

## Data Flow

### 1. Start Frame Flow
1. **Request**: `POST /api/frame/start`
2. **Payload**: `tableId`, `startedBy`, list of `players[]`.
3. **Logic**: Verifies table availability. Checks role limits (Customer: 1 frame).
4. **DB**: Sets `snooker_tables.is_available = false`. Inserts `frames` (`status = STARTED`). Inserts `frame_players`.
5. **Response**: Frame ID.

### 2. End Frame Flow
1. **Request**: `POST /api/frame/end/{frameId}`
2. **Payload**: `winnerId`, `looserId`.
3. **Logic**: `duration = endTime - startTime`. `totalAmount = duration * rate`. Assigns `paymentDue`.
4. **DB**: Updates `frames` (`status = ENDED`). Updates `snooker_tables` (`is_available = true`).
5. **Response**: Calculation summary (duration, amount, due).

### 3. Payment Settlement Flow
1. **Request**: `POST /api/payment/settle`
2. **Payload**: `userId`, `amount`, `mode` (CASH/UPI etc).
3. **Logic**: Iterates over chronologically ordered unpaid frames for user. Deducts amount until `0`.
4. **DB**: Inserts `payments`. Updates `frames.payment_due` and `payment_status`.

### 4. Kids Play Session Flow
1. **Request**: `POST /api/kids-session/start`
2. **Payload**: `childId`, `durationMinutes` (optional).
3. **DB**: Inserts `kids_play_sessions` (`status = STARTED`).
4. **Response**: DTO containing session boundaries.

### 5. Tournament Registration Flow
1. **Request**: `POST /api/tournaments/register`
2. **Payload**: `userId`, `tournamentIds[]`.
3. **Logic**: Executes deterministic pre-checks via `existsByTournamentIdAndUserId` to bypass hard JPA constraint faults natively.
4. **DB**: Generates unique `tournament_registrations`.

---

## Database

* **`users`**
    * Columns: `id`, `name`, `email`, `google_id`, `phone`, `role`, `is_active`
    * Meaning: Core identity entity. `role` defines permissions.
* **`snooker_tables`**
    * Columns: `id`, `table_name`, `rate_per_minute`, `is_active`, `is_available`
    * Meaning: Physical resources. `rate_per_minute` drives billing.
* **`frames`**
    * Columns: `id`, `table_id` (FK), `started_by` (FK: users), `winner` (FK), `looser` (FK), `status` (STARTED/ENDED/REJECTED), `payment_status` (UNPAID/PARTIAL/PAID), `start_time`, `end_time`, `duration_minutes`, `total_amount`, `payment_due`.
    * Meaning: The core transactional record tying a game session to a billable amount.
* **`frame_players`**
    * Columns: `id`, `frame_id` (FK), `user_id` (FK - nullable), `player_name`.
    * Meaning: The roster of participants linked to a given match.
* **`payments`**
    * Columns: `id`, `frame_id` (FK), `user_id` (FK), `amount`, `payment_method`, `payment_time`.
    * Meaning: Ledger for all transaction receipts to clear `frames` dues.
* **`customer_feedback`**
    * Columns: `id`, `user_id` (FK), `star_rating`, `feedback`.
    * Meaning: Basic review auditing.
* **`tournaments`, `tournament_registrations`, `tournament_matches`, `tournament_updates`**
    * Meaning: Encapsulates bracket configurations, pricing, and active linkage tracking for large scale snooker/pool event operations.
* **`children`, `kids_play_sessions`**
    * Meaning: A distinct sub-module independent of core `snooker_tables`. Tracks secondary demographic behaviors (play zone sessions over physical tables).

---

## APIs

```text
# USER API
GET  /api/user?email={email}                  - Fetch user by email
GET  /api/users/search?query={q}              - Auto-suggest/Search active users
GET  /api/users/player-summary?page=X&size=Y  - Paginated list of all users w/ frames played and due
POST /api/user/phone                          - Update user phone number

# AUTH API
POST /api/auth/google               - Basic Google payload reception (Sends Mail)
POST /api/auth/google-login         - Real login issuing JWT from token

# SNOOKER API
GET  /api/snooker/tables            - Fetch available tables

# FRAME API
POST /api/frame/start                           - Starts new match on a Table
GET  /api/frame/active?userId={id}              - Fetch actively running frame info
GET  /api/frame/user-ongoing?userId={id}        - Alias for Active Frame
GET  /api/frame/ongoing/today                   - All active frames for managers
GET  /api/frame/completed/today                 - All ended frames for today
GET  /api/frame/user-due?userId={id}            - Frames where user represents an outstanding debt
GET  /api/frame/history?userId={id}             - User's historical games
GET  /api/frame/total-due?userId={id}           - Returns aggregate outstanding BigDecimal
GET  /api/frame/{frameId}/players               - Roster of players for a frame
GET  /api/frame/{frameId}                       - Deep frame details
POST /api/frame/end/{frameId}                   - Settles an active frame
POST /api/frame/reject/{frameId}                - Aborts frame with zero payload/billing

# PAYMENT API
POST /api/payment/settle            - Submits a monetary settlement toward dues

# FEEDBACK API
POST /api/feedback                  - Saves customer feedback

# KIDS PLAY API
POST /api/kids-session/start                    - Starts playtime tracking
POST /api/kids-session/end                      - Computes playtime amounts
GET  /api/kids-session/active                   - Contextual dashboard session loading

# TOURNAMENT API
GET  /api/tournaments/active                    - Pulls active event definitions
POST /api/tournaments/register                  - Submits and differentiates arrays for deduplication
```

---

## Roles & Permissions

| Role | Permissions |
| :--- | :--- |
| **CUSTOMER** | Limited. Can start exactly 1 frame. Cannot approve own frames or reject frames. |
| **MANAGER** | Extended. Can start multiple simultaneous frames, manage operations, override table states, end frames on behalf of others. |
| **ADMIN** | Full. Standard administrative oversight logic. |
| **SUPER_ADMIN** | Full Control. Unrestricted access across database mapping. |

---

## Business Rules

1. **Max Active Frames**: A `CUSTOMER` can only initiate 1 active frame at any given time. Exceeding this limit throws an exception unless the user role is within `PRIVILEGED_ROLES` (Manager+).
2. **Table Locking**: `SnookerTable.isAvailable` acts as a distributed lock. No two frames can utilize the same table.
3. **Minimum Billing / Duration**: Backend guarantees at least a 1-minute duration for any completed frame (`if (duration <= 0) duration = 1;`).
4. **Payment Responsibility**: The `looser` parameter explicitly tracks the debt. The payment due logic cascades off this assigned entity.
5. **Partial Payments**: Fully automated iterative matching. A bulk payment spans multiple unpaid frames linearly (by oldest `startTime`) adjusting `paymentStatus` to `PARTIAL` if exact change isn't met.
6. **Player Requirement**: At least one player must be associated within `players[]` array under `StartFrameRequest`.
7. **Geo-Fencing Restriction**: Managed client-side natively. App refuses to call `Start Frame` if coordinates mismatch backend acceptable distance ranges.
8. **Dynamic Pricing for Extra Players**: Base table rate applies up to 2 players. Any additional player beyond 2 adds ₹0.5 per minute to the effective rate before calculating the final amount.

---

## Frontend Structure (Angular)

* `core/`: 
  * Identifies global abstractions (`constants`, `services`, `utils`).
  * Enforces state context via `guards` (e.g. `AuthGuard`) and `interceptors` (Token injection).
* `shared/`:
  * Presentational `components` re-used universally (e.g., buttons, modals, table elements).
* `features/`:
  * `auth/`: Login mechanics.
  * `dashboard/`: Overview landing page.
  * `snooker-frame/` & `start-frame/`: Real-time session handling mechanisms and initiation forms.
  * `managers-portal/`: Administrative dashboard for overview.
  * `my-game-history/`, `payment-settlement/`: Deep dives into individual ledger paths.
  * `kids-play/`: Session and child addition interfaces, isolated logic module allowing Managers to trigger via Parent Identity lookup.
  * `summer-olympics-registration/`: Multi-select grid parsing array registrations dynamically and displaying custom structural Modals.
* **Patterns**: Lazy-loaded feature routing via `app.routes.ts`. Emphasis on structured RxJs streaming for REST updates.

---

## Deployment

* **Platform**: Application is configured and deployed on **Render**.
* **Process**: CI/CD pipeline attaches to the Git repository.
* **Variables Needed**:
  * Spring Boot properties (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` pointing to Supabase PostgreSQL).
  * Google OAuth Client ID & Secret (`GOOGLE_CLIENT_ID`).
  * JWT Secret for standard signing (`JWT_SECRET`).
* **Connection Type**: Uses standard JDBC bindings over a secure PostgreSQL protocol.

---

## Notes & Known Edges

1. **Race Conditions**: Two users attempting to book the exact same `tableId` at near-simultaneous intervals might collide. The `isAvailable` boolean requires strict JPA pessimistic/optimistic locking if scale becomes high-concurrency. 
2. **Payment Allocation**: Settlements blindly pay down chronologically. Ensure managers map refunds manually as there is currently no standalone endpoint to "reverse" a `PaymentService.settlePayment` execution.
3. **Rejected Frames**: Leaves a `totalAmount = 0` artifact in the DB but immediately unlocks the table. Important for historical tracking.
