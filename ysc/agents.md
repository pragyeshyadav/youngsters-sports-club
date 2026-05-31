# agents.md: Youngsters Sports Club - AI Source of Truth

**Overview:**
Youngsters Sports Club (YSC) is a full-stack Angular + Spring Boot + PostgreSQL platform designed for managing a snooker club, cafe consumables, kids play sessions, and tournament registrations. 

This file is the **ultimate single source of truth** for any AI agent (Codex, Antigravity, Claude, GPT) or developer working in this repository. Read this deeply to understand system behavior before modifying code.

---

## 1. Project Architecture

* **Frontend Framework:** Angular 19 (Standalone components, `app.routes.ts` lazy loading, TypeScript 5.7). Features are grouped in `client/src/app/features/`, core logic in `core/`, and primitives in `shared/`.
* **Backend Framework:** Spring Boot 3.4.2 (Java 17). Standard multi-layer MVC architecture (`api`, `service`, `repository`, `entity`, `dto`).
* **Database:** PostgreSQL (hosted on Supabase) utilizing Hibernate/JPA. HikariCP connection pooling is tuned (max-pool-size: 10).
* **Deployment Platform:** Render (using environment variables for configuration overriding).
* **Dependency & Build:** Maven (`pom.xml`) uses `frontend-maven-plugin` to build Angular and package it within the Spring Boot `static/` folder for a unified fat-JAR deployment.
* **Environment Variables (Important):** 
  * `MAIL_USERNAME` / `MAIL_PASSWORD` (Gmail SMTP App Password)
  * `WHATSAPP_ACCESS_TOKEN` / `WHATSAPP_PHONE_NUMBER_ID`
  * `BREVO_API_KEY` / `BREVO_SENDER_EMAIL`

---

## 2. Authentication & User System

* **Google Login:** Primary entry point. Angular posts the Google payload to `POST /api/auth/google-login`.
* **User Entity:** Defines identity via `googleId` and tracks permissions via `UserRole` (`CUSTOMER`, `MANAGER`, `ADMIN`, `SUPER_ADMIN`).
* **Manual Users & Merging Flow (`UserService.mergeUserAccounts`):**
  * Managers can create "Manual Customers" via the portal (`MANUAL_USER_...`).
  * When a new Google login attempts to register a phone number that already belongs to a Manual Customer, a **Merge Operation** occurs.
  * The newly created Google user identity is "retired" (`merged_...` email, `MERGED_USER_...` googleId).
  * The existing manual user assumes the active Google ID and Email, effectively taking ownership of the Google login while preserving their debt history.
* **Phone Numbers:** Used heavily for WhatsApp notifications.

---

## 3. Snooker Module

* **Table Locking:** Handled by `SnookerTable`'s `is_available` flag. This acts as the physical concurrency lock.
* **Match Modes:**
  * Singles (1v1, 3-player, 5-player, 6-player): Typically 1 loser is billed.
  * Team (4-player / 2v2): Exactly 2 winners and 2 losers required. The total due is split evenly among losers.
* **Dynamic Rates:** Base rate covers up to 2 players. Additional players add ₹0.5/minute before final calculation.
* **Lifecycle:** `POST /api/frame/start` (locks table) -> `POST /api/frame/end/{frameId}` (calculates duration/dues and unlocks table) or `POST /api/frame/reject/{frameId}` (cancels frame).

---

## 4. Payment Settlement System

* **Orchestrator:** `PaymentService.java` (`POST /api/payment/settle` or `POST /api/payment/settle-by-date`).
* **Oldest-First Partial Settlement:** A single lump-sum payment (and optional discount) is allocated across all due modules in this exact order:
  1. Unpaid Snooker Frames (chronological)
  2. Unpaid Consumable Orders (chronological)
  3. Unpaid Kids Play Sessions (chronological)
* **Transactionality:** The ledger (`Payment` entity) tracks cash and discounts. Module dues (`Frame.paymentDue`, `ConsumableOrder.totalAmount`, `KidsPlaySession.totalAmount`) are directly decremented.
* **Status Updates:** Dues reaching 0 update their respective status to `PAID`. Otherwise, they become `PARTIAL` or remain `UNPAID`.

---

## 5. Kids Play Module

* **Independence:** Operationally separate from Snooker but relies on a special `SnookerTable` row named exactly `Kids Ocean Dream Land` to determine pricing (`rate_per_minute`).
* **Child Management:** Customers can add max 10 children.
* **Lifecycle:** Start session -> End session (computes cost = duration × rate) -> Reject session (zero duration/cost).
* **Payment Integration:** Dues are pulled into the unified Payment Settlement system.

---

## 6. Consumable / Cafe Module

* **Pricing Source of Truth:** `ConsumableItem` (DB driven). Frontend sends a cart of `itemId` + `quantity`. Backend recalculates line totals (`ConsumableService.createOrder`) to prevent tampering.
* **Structure:** One `ConsumableOrder` contains many `ConsumableOrderItem`s.
* **Status:** Starts as `UNPAID`. Aggregated in the Dashboard/Manager Portal dues system.
* **Stock Tracking:** Supported via `ConsumableItemStock`.

---

## 7. WhatsApp Integration

* **Service:** `WhatsAppService.java` using Meta Cloud API.
* **Capabilities:**
  * **Payment Settlement Notification:** Automatically sent upon successful partial or full settlement. Triggered post-DB-commit via Spring `TransactionSynchronizationManager`.
  * **Daily Visit Thank You:** Scheduled job for daily engagement.
* **Dry Run Mode:** Supports boolean toggles to log output without actually calling the external Meta API (useful for testing/staging).

---

## 8. Brevo Email Integration

* **Service:** `BrevoEmailService.java`.
* **Purpose:** Sends the daily summary of WhatsApp messages sent. 
* **Recipients:** Queries the DB for all active users with `ADMIN` or `SUPER_ADMIN` roles.
* **Templates:** Built dynamically via JSON bodies in the Java service.

---

## 9. Reports & Dashboard

* **Earnings Analytics:** `AnalyticsService.java` handles `GET /api/analytics/today-earnings`. Queries aggregated frame costs, partial settlements, and breaks down who owes what. Allows historical queries up to 60 days back.
* **Aggregated Summary:** `UserPaymentSummaryService.java` merges Frame dues, Consumable dues, and Kids Play dues into a single `UserPaymentSummaryDto` to display on the Customer Dashboard and Payment Settlement UI.

---

## 10. Manager Portal

* **Angular Implementation:** `client/src/app/features/managers-portal/`
* **Features (Collapsible Panels):**
  * **Today's Earnings:** Revenue and dues snapshot (historical date picker supported).
  * **Frames:** View Ongoing and Completed frames.
  * **Customers:** Add manual customers or update existing customers. Only Manual users can have their Names/Emails updated; Google users are locked.
  * **Child Management:** Search for a parent and manage their children globally.
  * **Player Summary:** Paginated view of total frames and total dues per player.

---

## 11. Database Documentation (JPA Entities)

* `User`: Core identity, roles, Google ID, Phone.
* `SnookerTable`: Physical tables & Kids Ocean Dreamland pricing config.
* `Frame`: Match lifecycle, start/end times, winners, losers, total amount.
* `FramePlayer`: Junction mapping players to a frame (holds partial split debts for team matches).
* `Payment`: Global ledger tracking cash/discount applications.
* `Child`: Child profiles linked to parents.
* `KidsPlaySession`: Independent duration billing for children.
* `ConsumableItem`, `ConsumableOrder`, `ConsumableOrderItem`: Cafe operations.
* `Tournament`, `TournamentRegistration`, `TournamentMatch`, `TournamentUpdate`: Summer Olympics event management.
* `CustomerFeedback`: Star ratings and reviews.

---

## 12. Scheduled Jobs / Cron Tasks

* **Location:** `DailyCustomerEngagementService.java`
* **Cron:** `@Scheduled(cron = "0 30 21 * * *", zone = "Asia/Kolkata")` (9:30 PM IST every day).
* **Job:** `sendDailyVisitThankYouMessages`.
* **Flow:** Finds all users who played a frame or bought consumables today -> Dispatches WhatsApp message -> Compiles results -> Dispatches Brevo Summary Email to Admins.

---

## 13. Known Bugs / Sensitive Areas

1. **Snooker Concurrency Locks:** Table locking relies on JPA updates. Concurrent requests can cause race conditions. Frontend utilizes button-loading states (`isStartingFrame`, `isEndingFrame`) to aggressively mitigate this.
2. **Team Mode State Issue (FIXED):** Previously, `maxLosersAllowed` in `start-frame.component.ts` incorrectly fell through to `1` for 4-player team matches. It now strictly enforces `2`, resolving a bug where the `Confirm` button wouldn't enable.
3. **Account Merge Deletion:** Merging an account does NOT delete the Google user; it "retires" them (`merged_...`). This retains their DB relations to prevent constraint violations.
4. **Oldest-First Subtraction:** Settlement directly reduces `total_amount` or `paymentDue`. The original gross invoice amount is overwritten on partial payments, which simplifies logic but obscures historical gross totals.
5. **No Lombok on Entities:** Do not use Lombok (`@Data`, `@Getter`, `@Setter`) on new JPA entities. Java 24 toolchain updates caused `TypeTag :: UNKNOWN` compile errors in this repo.

---

## 14. API Documentation (Key Endpoints)

* `GET /api/user?email={email}` : Retrieve User (used heavily by Angular for role checking).
* `POST /api/frame/start` : Initiate frame (requires `tableId`, `players[]`).
* `POST /api/frame/end/{frameId}` : Stop frame, compute cost.
* `POST /api/payment/settle` / `settle-by-date`: Resolves all debts oldest-first.
* `POST /api/consumables/order`: Creates unpaid cafe order.
* `GET /api/analytics/today-earnings`: Manager portal dashboard numbers.
* `POST /api/kids-session/start`: Starts Kids Dreamland timer.
* `GET /api/users/search?query={q}`: Global user search (requires min 3 chars).

---

## 15. UI/UX Conventions

* **Theme:** Premium, dark-mode focused, glassmorphism aesthetics.
* **Component Design:** Standalone angular components (`@Component({ standalone: true })`).
* **Form Handling:** Reactive forms generally preferred, but `ngModel` is heavily used in older features (like Manager Portal).
* **Validation:** Explicit disabling of submission buttons based on reactive getters (`isCustomerFormValid()`, `canEndFrame()`).
* **Loading States:** Explicit spinners and disabled buttons while API calls are in flight.
* **Mobile Responsiveness:** Relies heavily on flex-box and `@media (max-width: 768px)` in SCSS.

---

## 16. Developer Guidelines

1. **Avoid Lombok on Entities.** Stick to generating standard getters and setters.
2. **Schema Migrations:** The project relies on `spring.jpa.hibernate.ddl-auto=update`. Avoid writing native SQL files for migrations at this stage. Modify entities directly and let Hibernate update the schema.
3. **Build Testing:** Validate changes using `./mvnw -Dfrontend.skip=true test` and Angular compilation using `npx tsc -p tsconfig.app.json --noEmit`.
4. **Role Checks:** Guard features primarily on the frontend via `['MANAGER', 'ADMIN', 'SUPER_ADMIN'].includes(user.role)`, but ensure backend APIs inherently reject actions for unprivileged users.

---

## 17. Feature Dependency Mapping

* **Payment Settlement** -> Depends on (Snooker Frames, Consumables, Kids Play). A schema change to any of these three modules will break settlement logic.
* **User Merge** -> Depends on Google OAuth payload vs User Entity lookup by Phone. Changing phone number uniqueness will break merging.
* **Manager Earnings** -> Depends on `Payment` table for settled cash and `Frame` table for gross earnings.

---

## 18. Recommended Future Improvements

1. **Pessimistic Locking / Optimistic Locking (JPA):** Add `@Version` on `SnookerTable` to prevent race conditions during heavy concurrent bookings.
2. **Database Migration Tool:** Introduce Flyway or Liquibase to stop relying on `hibernate.ddl-auto=update`, making schema changes predictable in production.
3. **Immutable Ledgers:** Modify Consumables and Frames to store `grossAmount` alongside `remainingDue` so partial payments do not destroy the original transaction cost.
4. **Redis Caching:** Introduce caching for `/api/snooker/tables` and `/api/leaderboard/top-players` to reduce DB hits on dashboard loads.
