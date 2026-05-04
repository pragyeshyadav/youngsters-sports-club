# agents.md

## Overview
Youngsters Sports Club (YSC) is a full-stack Angular + Spring Boot + PostgreSQL platform for running the day-to-day operations of a snooker club and its adjacent businesses. The application now covers:

* snooker table booking and live frame tracking
* partial and full payment settlement
* manager/admin operational dashboards
* customer feedback
* consumable ordering and billing
* Kids Ocean Dreamland child session management
* Summer Olympics tournament registration

This file is intended to be the primary onboarding document for any AI agent or developer working in this repository. It should be read together with the live code when making changes.

---

## Architecture

**1. Frontend (Angular)**
* Standalone-component Angular app using lazy-loaded routes from `client/src/app/app.routes.ts`.
* Feature-first structure under `client/src/app/features/`.
* Shared UI primitives live in `client/src/app/shared/components/`.
* Global auth state and token handling live under `client/src/app/core/`.
* HTTP calls are made directly from feature components for most screens; there is limited centralized API abstraction.

**2. Backend (Spring Boot)**
* Standard layered architecture:
  * `api` -> controllers
  * `service` -> business logic
  * `repository` -> Spring Data / query layer
  * `entity` -> JPA models
  * `dto` -> request/response payloads
* JWT-based authenticated flow after Google login.

**3. Database**
* PostgreSQL hosted on Supabase.
* JPA/Hibernate entities mirror the DB tables directly.
* Current codebase relies on explicit query methods and native projections rather than a migration framework in-repo.

**4. Deployment**
* Deployed on Render.
* Frontend is built from the Angular workspace inside `client/`.
* Backend and frontend builds are often verified independently:
  * backend: `./mvnw -Dfrontend.skip=true test`
  * frontend type check: `./node_modules/.bin/tsc -p tsconfig.app.json --noEmit`

**5. Runtime / Tooling Notes**
* The project currently avoids Lombok in the new consumables entities because it caused compiler/runtime issues with newer Java toolchains (`TypeTag :: UNKNOWN` on Java 24 in this repo context).
* Monetary values are handled with `BigDecimal` in the backend and displayed as numbers on the frontend.

---

## Major Features

### 1. Authentication and User Bootstrap
* Google OAuth login is the entry point.
* Angular posts the Google payload to `/api/auth/google-login`.
* Backend creates or resolves a `User`, returns a JWT, and the frontend stores user details locally.
* Many screens then resolve the current backend user through `/api/user?email={email}`.

### 2. Phone Number Collection
* Dashboard prompts users without a phone number.
* `/api/user/phone` only saves a phone number when one is not already present.
* Existing phone values are intentionally not overwritten.

### 3. Dashboard
* Main landing screen after login.
* Displays:
  * greeting / identity block
  * due summary banner when total due crosses the configured threshold
  * start/end snooker CTA
  * feedback card with upgraded collapsible UI
  * available tables / leaderboard sections
  * role-specific navigation such as Manager Portal / Admin Page
  * Kids Ocean Dreamland entry card
  * Summer Olympics registration card
* CUSTOMER users are geofenced before being allowed to start a frame.

### 4. Snooker Frame Lifecycle
* Start frame flow is initiated from `/snooker-frame` and executed on `/start-frame`.
* Users select a table, search/select players, and call `POST /api/frame/start`.
* Ending a frame happens from the same `/start-frame` screen by opening an end-frame popup and calling `POST /api/frame/end/{frameId}`.
* Rejected frames call `POST /api/frame/reject/{frameId}`.
* Frontend now guards against duplicate submission with button-level loading states:
  * `isStartingFrame`
  * `isOpeningEndPopup`
  * `isEndingFrame`

### 5. Manager Portal
* Operational oversight screen for MANAGER / ADMIN / SUPER_ADMIN roles.
* Includes collapsible sections for:
  * today’s total earnings
  * today’s ongoing frames
  * today’s completed frames
  * consumable item ordering
  * paginated player summary / dues overview
* Today’s earnings uses a dedicated backend analytics endpoint and shows total earnings, total due, and due-player breakdown.

### 6. Consumable Ordering and Billing
* New business flow built on:
  * `consumable_items`
  * `consumable_orders`
  * `consumable_order_items`
* Manager Portal can create consumable orders for a selected user with multiple items in one submission.
* Backend calculates all line totals server-side from item prices.
* Consumable dues are integrated into:
  * `/api/user/payment-summary`
  * manager payment settlement
  * My Game History due summary
  * consumable history panel on My Game History

### 7. Payment Settlement
* Existing payment flow still centers on `/api/payment/settle`.
* Settlement now spans multiple business modules in oldest-first order:
  * frame dues
  * consumable dues
  * kids play dues
* Payment records are still stored in `payments`.
* Frame payments update `frames.payment_due` and `frames.payment_status`.
* Consumable and kids-play payments reduce their outstanding `total_amount` and update their payment status.

### 8. My Game History
* `/my-game-history` still shows core frame history.
* It now also shows:
  * `Frame Due`
  * `Consumable Due`
  * `Kids Due`
  * `Total Due`
* A collapsible `My Consumable History` section loads separately and is optimized not to block the full page.

### 9. Feedback System
* Dashboard contains a collapsible, premium-styled feedback card.
* Existing submission API remains `/api/feedback`.
* Logic still requires both star rating and text feedback.

### 10. Kids Ocean Dreamland Module
* Separate play-session module under `/kids-play`.
* Uses the shared auth/user system but remains operationally independent from snooker tables, except that pricing is read from a `snooker_tables` row named `Kids Ocean Dream Land`.
* CUSTOMER flow:
  * manage own children
  * add children (max 10)
  * start/end sessions per child independently
* MANAGER / ADMIN / SUPER_ADMIN flow:
  * search/select a parent
  * manage children in that selected parent context
  * start sessions for multiple children independently
  * monitor all active sessions in a collapsible `Playing Children` panel
  * end or reject sessions
* Branding is customized for this module with Kids Ocean Dreamland assets and styling.

### 11. Summer Olympics Registration
* Dedicated `/tournament-registration` feature.
* Supports registration against active tournaments.
* Backend distinguishes already-registered vs newly-registered entries and returns structured feedback for the UI.

### 12. Leaderboard / Admin Utilities
* Leaderboard data is served from `/api/leaderboard/top-players`.
* Admin and manager routes are present as standalone Angular screens, protected by the shared auth guard.

---

## Current Data Flow

### 1. Start Frame Flow
1. `POST /api/frame/start`
2. Payload: `tableId`, `startedBy`, `players[]`
3. Backend validates table/user constraints, creates `Frame` + `FramePlayer`
4. Table is marked unavailable
5. Frontend locks the CTA while request is in flight

### 2. End Frame Flow
1. `POST /api/frame/end/{frameId}`
2. Payload: `winnerId`, `looserId`
3. Backend computes duration and billable amount
4. Loser receives the due amount
5. Table is unlocked

### 3. Payment Settlement Flow
1. `POST /api/payment/settle`
2. Payload: `userId`, `amount`, `mode`
3. Backend totals outstanding dues across frames, consumables, and kids sessions
4. Payment is allocated oldest-first
5. `payments` records are created during allocation

### 4. Consumable Order Flow
1. Manager Portal builds a multi-line item request
2. `POST /api/consumables/order`
3. Backend loads active items, calculates totals from DB prices, creates order + order items transactionally
4. Order is saved as `UNPAID`

### 5. User Payment Summary Flow
1. `GET /api/user/payment-summary?userId={id}`
2. Backend aggregates:
  * frame due
  * consumable due
  * kids due
3. DTO returns module-specific values plus combined `totalDue`

### 6. My Consumable History Flow
1. User opens the collapsible panel on `/my-game-history`
2. `GET /api/consumables/my-history?userId={id}`
3. Backend joins orders, order items, and items
4. Frontend renders desktop table or mobile cards with status colors

### 7. Today’s Earnings Flow
1. Manager opens the earnings panel
2. `GET /api/analytics/today-earnings`
3. Backend returns:
  * total earnings today
  * total due today
  * loser breakdown for due amounts

### 8. Kids Play Session Flow
1. Parent or privileged staff selects child context
2. `POST /api/kids-session/start`
3. Backend saves `start_time`, `rate_per_minute`, `status = STARTED`, `payment_status = UNPAID`
4. Active sessions are surfaced through `GET /api/kids-session/active`
5. `POST /api/kids-session/end` computes duration and amount
6. `POST /api/kids-session/reject` marks the session cancelled with zero charge

### 9. Tournament Registration Flow
1. `GET /api/tournaments/active`
2. User submits selected tournaments to `POST /api/tournaments/register`
3. Backend deduplicates and returns structured registration results

---

## Database Model

### Core Tables
* **`users`**
  * Core identity table
  * Stores role, Google identity, contact details, active flag

* **`snooker_tables`**
  * Physical snooker resources
  * Also reused for Kids Ocean Dreamland pricing by looking up table name `Kids Ocean Dream Land`

* **`frames`**
  * Main snooker billing transaction
  * Tracks started by, winner, looser, timing, amount, and payment status

* **`frame_players`**
  * Roster entries for a frame
  * Supports named players and user-linked players

* **`payments`**
  * Ledger-style table used during settlement
  * Payments may correspond to frame dues or non-frame dues, so `frame_id` can be null

* **`customer_feedback`**
  * Stores star rating and written feedback

### Consumables
* **`consumable_items`**
  * Master data for items, price, active status, created timestamp

* **`consumable_orders`**
  * Order header for a user
  * Stores total outstanding amount and payment status

* **`consumable_order_items`**
  * Order lines
  * Stores quantity, unit price, and line total

### Kids Play
* **`children`**
  * Child profile linked to a parent user
  * Holds name, DOB, address, school

* **`kids_play_sessions`**
  * Stores child, parent, start/end times, duration, rate, total amount, payment status, lifecycle status

### Tournaments
* **`tournaments`**
* **`tournament_registrations`**
* **`tournament_matches`**
* **`tournament_updates`**

---

## APIs

```text
# HEALTH / MISC
GET  /api/health                               - Basic health check

# USER API
GET  /api/user?email={email}                   - Fetch current backend user by email
POST /api/user/phone                           - Save phone number if not already set
GET  /api/user/payment-summary?userId={id}     - Frame + consumable + kids due summary
GET  /api/users/search?query={q}               - Search users by name
GET  /api/users/player-summary?page=X&size=Y   - Paginated player summary for managers

# AUTH API
POST /api/auth/google                          - Google payload entrypoint
POST /api/auth/google-login                    - Google login issuing JWT

# SNOOKER TABLE API
GET  /api/snooker/tables                       - Available active tables

# FRAME API
POST /api/frame/start                          - Start a frame
GET  /api/frame/active?userId={id}             - Active frame response
GET  /api/frame/user-ongoing?userId={id}       - Alternate active-frame lookup
GET  /api/frame/ongoing/today                  - Today’s ongoing frames
GET  /api/frame/completed/today                - Today’s completed frames
GET  /api/frame/user-due?userId={id}           - Due frames for a user
GET  /api/frame/history?userId={id}            - Historical frames
GET  /api/frame/total-due?userId={id}          - Frame-only due total
GET  /api/frame/{frameId}/players              - Player roster for a frame
GET  /api/frame/{frameId}                      - Frame details
POST /api/frame/end/{frameId}                  - End frame and compute billing
POST /api/frame/reject/{frameId}               - Reject frame

# PAYMENT API
POST /api/payment/settle                       - Settle frame/consumable/kids dues

# FEEDBACK API
POST /api/feedback                             - Submit customer feedback

# ANALYTICS API
GET  /api/analytics/today-earnings             - Today’s earnings + due breakdown

# CONSUMABLE API
GET  /api/consumables/items/search?query={q}   - Search active consumable items
POST /api/consumables/order                    - Create consumable order
GET  /api/consumables/orders/due?userId={id}   - Unpaid consumable order lines
GET  /api/consumables/my-history?userId={id}   - Consumable history for a user

# CHILD API
POST /api/children                             - Add child profile
GET  /api/children/by-parent?parentUserId={id} - List children for a parent

# KIDS PLAY API
POST /api/kids-session/start                   - Start kids play session
POST /api/kids-session/end                     - End kids play session
POST /api/kids-session/reject                  - Cancel kids play session
GET  /api/kids-session/active                  - All active sessions or by parent via parentUserId

# LEADERBOARD API
GET  /api/leaderboard/top-players              - Top players leaderboard

# TOURNAMENT API
GET  /api/tournaments/active                   - Active tournaments
POST /api/tournaments/register                 - Register user for tournaments
```

---

## Roles and Permissions

| Role | Current Effective Capabilities |
| :--- | :--- |
| **CUSTOMER** | Can manage own phone number, start one snooker frame at a time, manage own kids-play children and sessions, submit feedback, view own dues/history, register for tournaments. |
| **MANAGER** | Can start multiple snooker frames, use Manager Portal, settle payments for users, create consumable orders, view today’s analytics, manage kids-play across parent contexts, end/reject sessions and frames. |
| **ADMIN** | Same operational capabilities as manager plus admin screens and elevated oversight. |
| **SUPER_ADMIN** | Full unrestricted admin behavior across all modules. |

Important frontend convention:
* Most privileged UI checks are performed client-side with role checks such as:
  * `MANAGER`
  * `ADMIN`
  * `SUPER_ADMIN`
* CUSTOMER-only restrictions still need to be respected in backend logic where applicable.

---

## Business Rules

1. **Customer snooker concurrency limit**
   * A `CUSTOMER` can only initiate one active snooker frame at a time.
   * Manager+ roles are exempt.

2. **Table availability is the snooker lock**
   * `snooker_tables.is_available` controls snooker table booking.
   * Ending or rejecting a frame must unlock the table.

3. **Minimum frame billing duration**
   * Completed snooker frames are billed for at least 1 minute.

4. **Dynamic snooker rate for extra players**
   * Base rate applies up to 2 players.
   * Every player after 2 adds `₹0.5/minute` before final total calculation.

5. **Payment responsibility**
   * `frames.looser` is the debtor for snooker frame dues.

6. **Partial settlements are oldest-first**
   * Frame dues are settled in chronological order.
   * Consumable and kids dues are then reduced in sequence.

7. **Consumable pricing must come from DB**
   * Frontend-selected prices are not trusted.
   * Backend recalculates every line item from `consumable_items.price`.

8. **Consumable orders start unpaid**
   * New orders are created with `payment_status = UNPAID`.

9. **Kids pricing comes from the configured table row**
   * `Kids Ocean Dream Land` in `snooker_tables` is the source of `rate_per_minute`.
   * Missing configuration should be treated as an application error.

10. **One active kids session per child**
   * A child cannot have multiple active kids-play sessions simultaneously.

11. **Child ownership validation**
   * Child and kids-session operations validate against the parent user context.

12. **Maximum children per parent**
   * Frontend and service logic enforce a limit of 10 child profiles per parent.

13. **Kids session reject behavior**
   * Rejecting a kids session sets zero duration/amount and marks it cancelled.

14. **Geo-fencing is frontend-enforced**
   * CUSTOMER start-frame access is blocked when browser geolocation shows the user outside the club radius.

15. **Feedback requires both rating and text**
   * Empty feedback or missing stars should not be submitted.

16. **Duplicate-click protection exists in key snooker actions**
   * Start-frame and end-frame actions intentionally disable repeated submission while in flight.

---

## Frontend Structure

* `client/src/app/core/`
  * auth models, services, guards, interceptors, constants

* `client/src/app/shared/`
  * reusable UI such as brand title, club logo, and common presentational pieces

* `client/src/app/features/auth/`
  * login screen

* `client/src/app/features/dashboard/`
  * landing page
  * available tables
  * leaderboard
  * feedback card
  * Kids Ocean Dreamland and tournament CTAs

* `client/src/app/features/snooker-frame/`
  * table selection / route into frame start flow

* `client/src/app/features/start-frame/`
  * player selection
  * start frame
  * manage/end current frame

* `client/src/app/features/managers-portal/`
  * today’s earnings
  * ongoing/completed frames
  * consumables ordering
  * player summary

* `client/src/app/features/payment-settlement/`
  * user search
  * aggregated due summary
  * frame + consumable dues
  * settlement popup

* `client/src/app/features/my-game-history/`
  * frame history
  * due summary
  * consumable history panel

* `client/src/app/features/kids-play/`
  * children management
  * active sessions
  * manager parent-selection flow
  * branded Kids Ocean Dreamland UI

* `client/src/app/features/summer-olympics-registration/`
  * tournament registration UX

---

## Backend Hotspots

Agents usually need these files first:

* `src/main/java/com/youngstersclub/app/service/FrameService.java`
  * snooker lifecycle, dues, leaderboard, history

* `src/main/java/com/youngstersclub/app/service/PaymentService.java`
  * multi-module settlement orchestration

* `src/main/java/com/youngstersclub/app/service/UserPaymentSummaryService.java`
  * unified due aggregation

* `src/main/java/com/youngstersclub/app/service/ConsumableService.java`
  * item search, order creation, due/history projection mapping

* `src/main/java/com/youngstersclub/app/service/KidsPlayService.java`
  * kids-session lifecycle, active session queries, due settlement

* `src/main/java/com/youngstersclub/app/service/ChildService.java`
  * child ownership and add/list flows

* `src/main/java/com/youngstersclub/app/service/AnalyticsService.java`
  * today’s earnings computation

---

## Known Edges and Cautions

1. **Concurrent snooker starts can still race at backend level**
   * Frontend duplicate-click protection exists, but true concurrency safety still depends on table-state checks and could benefit from stronger DB locking in the future.

2. **Settlement ledger is shared across modules**
   * `payments` is reused for frame, consumable, and kids-play settlements.
   * Some non-frame payments are saved with `frame_id = null`.

3. **Consumable and kids partial status handling is simple**
   * Current code reduces `total_amount` directly as payments are applied.
   * This means the stored `total_amount` on unpaid records behaves like remaining due, not immutable original gross amount.

4. **User search is broad**
   * `/api/users/search` currently returns general user matches.
   * Some screens filter roles client-side, for example parent selection in Kids Play.

5. **Frontend role checks are important but not sufficient**
   * New sensitive features should not rely only on Angular visibility checks.

6. **agents.md must be kept in sync manually**
   * This file is documentation only.
   * Any major feature added to dashboard, manager portal, payment summary, consumables, kids-play, or tournaments should be reflected here.
