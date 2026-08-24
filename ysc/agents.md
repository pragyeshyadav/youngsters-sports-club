# AGENTS.md: Youngsters Sports Club SaaS Engineering Guide

## Purpose

Youngsters Sports Club (YSC) is a multi-organization, multi-branch club-operations SaaS. It combines public marketing pages with authenticated club operations for snooker, cafe consumables, kids play, games, tournaments, payments, communications, and reporting.

This document records the **current implementation**, its safety contracts, and the remaining SaaS hardening work. Read this file and `PROJECT_MASTER_CONTEXT.md` before changing organization, branch, payments, due calculation, reporting, or communications behavior.

Do not treat historic phase notes as current behavior when the code contradicts them. The codebase has implemented many branch-aware phases already.

## Repository Map

| Area | Location |
| --- | --- |
| Spring Boot source | `src/main/java/com/youngstersclub/app/` |
| Entities | `src/main/java/com/youngstersclub/app/entity/` |
| Controllers | `src/main/java/com/youngstersclub/app/api/` |
| Services | `src/main/java/com/youngstersclub/app/service/` |
| Repositories | `src/main/java/com/youngstersclub/app/repository/` |
| DTOs | `src/main/java/com/youngstersclub/app/dto/` |
| Backend tests | `src/test/java/com/youngstersclub/app/` |
| Angular application | `client/src/app/` |
| Angular features | `client/src/app/features/` |
| Angular core and context | `client/src/app/core/` |
| Shared Angular UI | `client/src/app/shared/` |
| Public landing assets | `src/main/resources/static/images/landing/` |
| Configuration | `src/main/resources/application.properties` |
| Docker build | `Dockerfile` |
| Lightsail deploy workflow | `.github/workflows/deploy-develop.yml` |

## Runtime Architecture

- **Frontend:** Angular 19 standalone components, TypeScript 5.7, Angular Router, reactive forms and existing `ngModel` flows.
- **Backend:** Spring Boot 3.4.2 on Java 17, JPA/Hibernate, PostgreSQL, Maven.
- **Build:** `frontend-maven-plugin` builds Angular and packages the distribution into Spring Boot static resources for a single deployable application.
- **Persistence:** PostgreSQL with HikariCP. Use database-side filtering, aggregation, pagination, projections, and branch predicates instead of materializing large collections.
- **Caching:** Spring Cache with Caffeine. The monthly leaderboard cache is bounded to 100 entries with a 10-minute expiry. Cache only final DTOs, never JPA entities.
- **Background execution:** scheduling and async processing are enabled. External notifications must be best-effort and post-commit where they depend on a committed transaction.

## Public Site and Routing

Angular routes are defined in `client/src/app/app.routes.ts`.

- `/` is the public `LandingPageComponent`; it must render for both authenticated and unauthenticated visitors.
- `/login` renders the existing Google Login component for guests. `guestGuard` redirects an already authenticated browser session using the existing application destination behavior.
- Protected application routes include `/dashboard`, `/snooker-frame`, `/start-frame`, `/my-game-history`, `/admin-page`, `/managers-portal`, `/payment-settlement`, `/kids-play`, and `/tournament-registration`.
- Do not rewrite Google OAuth, session persistence, role routing, logout, or dashboard navigation when changing the landing page.
- The public landing page is static Angular content. It includes real club, kids-play, cricket academy, founder, manager, tournament, and testimonial content. Keep public-page changes frontend-only unless a safe public API is explicitly required.
- Landing images are local assets. Do not hotlink or scrape Google/Instagram content at runtime. Keep large images optimized and below-the-fold images lazy loaded.

## Identity and Security: Current Reality

### Current implementation

- Google Identity Services is initialized in Angular. The client posts Google credentials to `POST /api/auth/google-login`.
- The browser stores authenticated user/token information and sends request metadata through the existing HTTP interceptor, including `Authorization` and `X-User-Email`.
- Backend controllers/services commonly resolve the actor using the user email/header through the current application convention.
- Roles are `CUSTOMER`, `MANAGER`, `ADMIN`, and `SUPER_ADMIN`.
- Manual customer accounts can be merged into a Google identity while preserving operational history. Do not break the `UserService` merge/retirement flow.

### SaaS hardening gap

There is no central Spring Security filter chain or server-side bearer-token verification protecting every API. The current header-based identity flow must **not** be represented as complete SaaS-grade authentication/authorization.

Before onboarding unrelated customer organizations in a production SaaS model, prioritize:

1. Server-side verification of Google/JWT credentials.
2. A Spring Security filter chain with default-deny API rules.
3. Removal of trust in caller-controlled identity headers.
4. Controller-level authorization backed by the authenticated principal.
5. Audit logging for context switches and sensitive write actions.

Do not expose secret values in source code, documentation, logs, screenshots, or commits. Configuration has historically contained sensitive fallback values; move every credential to deployment secrets and rotate any credential that was committed.

## SaaS Tenancy and Context

### Core model

- `Organization`: tenant root.
- `Branch`: physical operating location belonging to one organization.
- `OrganizationUser`: a user's membership/role in an organization, with a base branch.
- `UserBranchAccess`: additional authorized staff branch access.
- `OrganizationSettings`: organization-owned settings.

The source of truth is `OrganizationContextService` and the `ActiveContext` model. Relevant endpoints are under `/api/context`, `/api/organizations`, `/api/branches`, and `/api/context/change`. Angular consumes this through `client/src/app/core/.../organization-context.service.ts` and exposes context/branch observables.

### Mandatory branch rule

Every operational request must follow:

`authenticated actor -> organization membership -> selected organization -> selected authorized branch -> operation`

Never trust a frontend-provided branch or organization ID as an authorization decision. Derive the active context server-side and verify branch ownership/access.

Use the historical branch persisted on the operational record. Do not infer historical ownership from a user's base branch, a table's current branch, or a current UI selection.

On Angular branch changes, clear stale state, cancel/ignore stale requests, clear selections/dialogs where applicable, then reload scoped data. Do not read branch state independently from local storage in feature components.

## Branch-Scoped Domain Modules

### Snooker tables and frames

- `SnookerTable` and `Frame` are branch-owned.
- Start Frame resolves active context, validates table ownership/access, and persists `Frame.branch` as historical truth.
- End/reject/load actions must scope IDs by branch where applicable; do not allow direct cross-branch frame access.
- Table availability is the concurrency lock. Preserve loading states and avoid unrelated changes to lifecycle calculations.
- Leaderboards count completed frames in the selected month using the persisted frame branch and existing winner semantics.
- Monthly leaderboard output is Top 10 on the backend, deterministic, branch-specific, and cached by `branchId:year:month` for ten minutes.

### Customers and branch search

- `GET /api/users/search?query` is a legacy global search and must not be casually reused for operational branch lookup.
- `GET /api/users/search/current-branch` is the branch-aware search used by Start Frame and Play Zone parent selection.
- Preserve the existing minimum search threshold, name/email/phone matching, result DTO, debounce, cancellation, and result limit.
- Branch eligibility is organization membership plus base branch or active explicit branch access.

### Due calculation and `user_dues`

- `CustomerBranchDueCalculatorService` is the reusable branch due calculator and returns `CustomerBranchDue` using `BigDecimal`.
- It calculates frame, consumable, kids-play, and game-activity due only from current-branch operational records.
- `PendingDueService` is the shared retrieval/batch facade used by downstream screens.
- `UserDueService` synchronizes `user_dues` by `(user_id, branch_id)`. Treat `user_dues` as a synchronized cache, not the sole financial truth where operational unpaid records are available.
- Never use `double` or `float` for money.

### Payments and earnings

- `PaymentService` settles only unpaid records belonging to the active branch, creates branch-owned payments, and preserves transaction boundaries.
- Payment history, selected-date views, discounts, payment mode, and earnings must be branch-filtered in the backend.
- `AnalyticsService`, `UserPaymentSummaryService`, and related repositories were memory-hardened; preserve aggregation queries and avoid rebuilding full frame/order/session lists just to calculate totals.
- Payment/settlement changes are high risk: do not refactor allocation behavior while making tenancy changes.

### Consumables and inventory

- `ConsumableItem`, `ConsumableItemStock`, and `ConsumableOrder` are branch aware.
- Items shown/orderable must belong to the active branch; stock updates are scoped to item plus branch.
- Order items derive their branch through the parent order. Reports must filter by branch.

### Kids play and games

- `KidsPlaySession` is branch-owned; parents/children remain organization-level reusable identities.
- Start/end/list actions must derive context and scope session access by branch.
- `Game` and `GameActivityOrder` are branch-owned. Price comes from the selected branch game record.
- Current branch due/settlement logic includes kids play and game activity dues.

### Tournaments

- `Tournament` is branch-owned; registrations/matches/updates inherit access from the parent tournament.
- `GET /api/tournaments/active` returns active tournaments for the current branch; do not hardcode an event name such as “Summer Olympics 2K26.”
- Registration preserves `successfullyRegistered` and `alreadyRegistered` results.
- On at least one new registration, `TournamentService` schedules exactly one admin Brevo notification after transaction commit. The email is best effort and must never rollback registration or change the response.
- Public landing-page tournaments are static marketing content; authenticated tournament registration uses the database-backed module.

### Feedback, expenses, and player summary

- `CustomerFeedback` is branch-scoped.
- `BranchExpense` stores organization, branch, amount (`BigDecimal`), payer, creator, active status, date, notes, and expense type.
- `BranchExpenseService` derives org/branch/creator from context, validates eligible payer roles and branch access, and lists active expenses by start-inclusive/end-exclusive month range.
- The Manager Portal Show All Players flow uses database pagination/sorting and shared due retrieval. Avoid reintroducing all-player in-memory sorting or N+1 due queries.

## Communications and Scheduled Jobs

- `WhatsAppService` uses Meta Cloud API. Keep dry-run behavior available and do not log customer data unnecessarily.
- `BrevoEmailService` is the single Brevo `/v3/smtp/email` implementation. Reuse it; do not create another HTTP email client.
- Tournament registration notification recipient is configuration-driven through `TOURNAMENT_REGISTRATION_NOTIFICATION_EMAIL`; sender is separately configured by `BREVO_SENDER_EMAIL`.
- Daily engagement, payment reminders, birthday messages, and Brevo summaries are scheduled in IST. Review organization/branch grouping and de-duplication before changing them.
- Scheduled jobs can be memory-intensive. Prefer repository projections, bounded batching, and streaming/page processing. Never build unbounded cross-organization customer lists.

## Manager, Admin, and Customer Experience

- **Dashboard:** branch-sensitive tables, current activity, leaderboard, Kids Ocean Dreamland, and active tournament entry points.
- **Manager Portal:** earnings/date analysis, ongoing/completed frames, manual customer update, children, paginated player summary, branch expenses, inventory/consumables, and related operational panels.
- **Admin:** administration, notification broadcast, and organization-aware controls. Any all-organization notification feature must explicitly validate the requested organization/branches.
- **Customer:** Google login, game history, payment views, kids play, tournament registration, and feedback flows.

Preserve mobile behavior, loading/error states, reactive request cancellation, and existing API shapes unless a deliberate versioned migration is approved.

## Data Access and Performance Rules

1. Filter by organization/branch in SQL before search, sorting, pagination, or aggregation.
2. Prefer projections and aggregate queries for reports/due summaries.
3. Do not use unbounded `findAll`, unbounded history, or fetch joins across several collections for dashboard endpoints.
4. Avoid N+1 lookups; batch load branch dues and related values.
5. Keep Top 10 and pagination limits enforced by the backend.
6. Use start-inclusive/end-exclusive date/time ranges.
7. Preserve configured business timezone behavior (IST where used) and never let browser timezone silently redefine reporting periods.
8. Add indexes only after confirming a query/index need; do not add speculative duplicates.

## API and Error Conventions

- Resolve actor/context in the backend, not from UI state.
- Authentication/context failures use the project-standard response; cross-branch operations must be rejected without leaking another branch's data.
- Invalid month/year, IDs, and requests return consistent client errors.
- Empty branch/month searches return `200` with `[]`, not a fallback to another branch or organization.
- Return DTOs, not JPA entities or unrelated personal data.
- Keep logs structured and avoid phone numbers, tokens, credentials, and full personal data in normal INFO logs.

## Configuration and Deployment

Required deployment configuration includes database credentials/URL, Google client ID, WhatsApp credentials, Brevo credentials/sender, notification recipient, and any CORS/public-host settings. Use deployment secret stores (`.env` on server or GitHub Actions secrets), never committed values.

Current Lightsail deployment automation:

- `.github/workflows/deploy-develop.yml` deploys on a `develop` push or manual dispatch.
- It connects through SSH using GitHub secrets and runs `/opt/ysc/deploy.sh`.
- The server workflow pulls source, builds `ysc-app:develop`, replaces the `ysc-app` container, uses `/opt/ysc/config/.env`, and binds application port `127.0.0.1:8080` for the reverse proxy.

Validate the target environment after deploy: `/`, `/login`, authenticated dashboard, context switching, active tournament endpoint, and application health.

## Testing and Build Commands

Run focused tests for touched modules, then the relevant regression set:

```bash
./mvnw -Dfrontend.skip=true test
npx tsc -p client/tsconfig.app.json --noEmit
npm --prefix client run build
```

The Maven frontend build is also part of `mvn clean install`. Keep Angular component tests focused on DOM/routing behavior and backend tests focused on context resolution, branch isolation, repository predicates, and transaction behavior.

Branch-scoped tests must prove at minimum:

- a manager with access to Satna and Rewa only receives the selected branch's records;
- direct ID manipulation cannot read/write another branch;
- branch changes clear stale UI state and stale responses cannot overwrite the new state;
- same user can have separate branch totals/dues;
- no organization-wide fallback is returned for an empty branch result.

## Change Checklist for Agents

Before coding:

1. Read this file and `PROJECT_MASTER_CONTEXT.md`.
2. Inspect every caller before changing an endpoint/service/repository method.
3. Confirm whether the data is tenant-scoped, branch-scoped, organization-scoped, or global identity data.
4. Reuse `OrganizationContextService`, `PendingDueService`, `CustomerBranchDueCalculatorService`, `BrevoEmailService`, and existing Angular context services where applicable.
5. Identify date/timezone and financial precision requirements.

Before merging:

1. Confirm no frontend-supplied organization/branch ID became trusted.
2. Confirm historical records use their persisted branch.
3. Confirm transaction-critical writes still commit/rollback together.
4. Keep external messaging post-commit and best effort.
5. Confirm backend-side pagination/limits and database-side filtering.
6. Test mobile layouts for new public/manager UI.
7. Run the stated validation commands.
8. Do not revert unrelated work in a dirty tree and do not amend commits unless asked.

## Current Risks and SaaS Roadmap

The product is materially branch-aware, but it is not yet fully hardened for untrusted multi-tenant internet access. Prioritize these before broad commercial onboarding:

1. Central server-side authentication/authorization and removal of trusted identity headers.
2. Secret rotation and removal of all configuration fallbacks containing live credentials.
3. Formal database migrations with Flyway/Liquibase instead of relying only on `ddl-auto=update` or manual production SQL.
4. Auditing, tenant provisioning/onboarding, tenant suspension, and support/admin impersonation controls.
5. Stronger table concurrency control (`@Version` or a carefully designed lock strategy).
6. Immutable financial ledger fields for original amount versus remaining due.
7. Operational monitoring, backups/restores, rate limits, and privacy/retention policy.
8. Explicit public versus internal API boundaries and CORS restrictions per production host.

Implement the roadmap incrementally. Do not collapse organization-wide reporting, branch operations, and global user identity into one query or one large migration.
