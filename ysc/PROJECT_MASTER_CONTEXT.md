# PROJECT_MASTER_CONTEXT.md

Version: 2.0 Status: Master Context Document

> This document is the single source of truth for AI coding agents
> (Codex, Claude Code, Cursor, ChatGPT, Gemini, etc.). Read this
> completely before implementing any feature.

# Vision

Youngsters Sports Club is evolving from a single-branch management
application into a multi-tenant SaaS platform supporting multiple
organizations and branches while preserving backward compatibility.

## Current Tech Stack

-   Backend: Java 17 + Spring Boot
-   Frontend: Angular
-   Database: PostgreSQL (Supabase)
-   Hosting: Render
-   Authentication: Google OAuth + Manual Customer Creation
-   Messaging: WhatsApp Cloud API
-   Email: Brevo
-   Reports: Admin / Manager Portal

# Current Functional Modules

-   Customer Management
-   Manual Customer Creation
-   Customer Merge after Google Login
-   Child Management
-   Snooker Frame Management
-   Multi-player Frames (2--6 players)
-   Team and Singles Support
-   Kids Ocean Dreamland Play Sessions
-   Generic Game Activities
-   Consumables & Inventory
-   Payment Settlement
-   Pending Due Calculation
-   WhatsApp Automation
-   Brevo Summary Emails
-   Scheduled Jobs
-   Tournament Management
-   Customer Feedback
-   Reports & Analytics

# Current Database

Production schema contains (among others):

-   users
-   children
-   frames
-   frame_players
-   payments
-   user_dues
-   snooker_tables
-   games
-   kids_play_sessions
-   game_activity_orders
-   consumable_items
-   consumable_item_stock
-   consumable_orders
-   consumable_order_items
-   customer_feedback
-   tournaments
-   tournament_matches
-   tournament_registrations
-   tournament_updates

Never redesign these abruptly. Evolve them.

# Business Model (Future)

## Organization

Represents one business/customer using the platform.

Examples:

-   Youngsters Sports Club & Kids Ocean Dreamland
-   RR Snookers Club
-   ABC Indoor Sports

Each organization is completely isolated.

Customers, managers, reports and data never cross organizations.

## Branches

Each organization owns one or more branches.

Example:

Youngsters Sports Club & Kids Ocean Dreamland

-   Satna
-   Rewa
-   Jabalpur
-   Bhopal

RR Snookers Club

-   Satna
-   Nazirabad

## Customer Rules

-   Customer belongs to exactly one Organization.
-   Every customer has exactly one Base Branch within that Organization.
-   Customer can visit ANY branch of that Organization.
-   Customer should never be duplicated across branches.
-   Same phone number in another Organization represents another
    customer.

## Manager Rules

-   Manager belongs to Organization.
-   Manager may be assigned to one or many branches.
-   Branch access is controlled through mapping.

## Owner Rules

-   One or more Owners may exist.
-   Owners can access every branch within their organization.

# Architecture

Organization ├── Branch (Satna) ├── Branch (Rewa) ├── Branch (Jabalpur)
└── Branch (Bhopal)

Customers └── Belong to Organization

Managers └── Belong to Organization └── Assigned to one or more Branches

Operational Data ├── Frames ├── Payments ├── Consumables ├── Kids Play
├── Games ├── Inventory ├── Reports ├── Tournaments └── WhatsApp Logs

Every user must have one base branch associated with them.

Every operational record belongs to a Branch.

# Database V2

New master tables:

-   organizations
-   branches
-   organization_users
-   user_branch_access

Existing transactional tables will gradually receive:

-   organization_id
-   branch_id

Customer table receives:

-   organization_id
-   base_branch_id

# Migration Strategy

## Phase 1

Create new master tables only.

## Phase 2

Seed current production:

Organization: Youngsters Sports Club & Kids Ocean Dreamland

Branches: - Satna - Rewa

## Phase 3

Add nullable organization_id and branch_id to transactional tables.

## Phase 4

Backfill production data by mapping all historical records to the
default organization and branch.

## Phase 5

Incrementally update backend APIs, repositories and Angular UI to use
organization/branch aware queries while keeping backward compatibility.

## Phase 6

After validation: - make foreign keys NOT NULL - enforce constraints -
remove legacy assumptions.

# Development Principles

-   Prefer additive schema changes.
-   Never break production.
-   Never duplicate business logic.
-   Reuse services.
-   Reuse repositories.
-   Reuse payment calculation logic.
-   Continue batch processing on failures.
-   Preserve mobile responsiveness.
-   Keep migrations reversible.

# AI Agent Rules

Before implementing: 1. Read this document. 2. Follow the migration
phases. 3. Never skip directly to destructive schema changes. 4.
Preserve backward compatibility. 5. Prefer reusable abstractions
(strategy/factory patterns). 6. Avoid duplicate SQL and duplicate
calculations.

# Current Integrations

-   WhatsApp Cloud API
-   Brevo Email
-   Google OAuth
-   Scheduled Jobs
-   Supabase PostgreSQL

# Future Roadmap

-   Multi-Organization
-   Multi-Branch
-   Branch-wise Reports
-   Branch-wise Inventory
-   Branch-wise Payments
-   Branch-wise Games
-   Branch-wise Staff
-   AI Assistant ("Ask Youngsters Sports Club AI")
-   AI Analytics
-   Promotion Engine
-   Customer Segmentation
-   Birthday Automation
-   Payment Due Automation
-   Offer Campaigns
-   Franchise Support

# Long-term Goal

Build a scalable SaaS platform for indoor sports clubs and family
entertainment centers where each organization manages multiple branches
from one system while keeping customer identities unified within the
organization and all operational data accurately scoped to branches.

---

# Planned Phase: Make All Modules Organization and Branch Aware

This phase is the next approved migration step and should be treated as a **phased rollout plan**, not a single bulk implementation.

## Current State

Database migration is complete, including backfill and `NOT NULL`
enforcement on `branch_id` for key operational tables. Application code
must now be migrated to consistently read and write using the current
authenticated organization and branch context.

Operational tables now containing mandatory `branch_id` include:

-   `snooker_tables`
-   `frames`
-   `payments`
-   `user_dues`
-   `kids_play_sessions`
-   `games`
-   `game_activity_orders`
-   `consumable_items`
-   `consumable_item_stock`
-   `consumable_orders`
-   `customer_feedback`
-   `tournaments`

Historical data has already been backfilled.

## Core Runtime Rule

Every request must resolve:

Authenticated User -> Current Organization -> Current Branch -> Role and
Branch Authorization

All reads, writes, settlement operations, reports, schedulers and
notifications must use this validated context.

Never trust arbitrary `organizationId` or `branchId` from the frontend
without backend verification through:

-   `organization_users`
-   `user_branch_access`
-   active organization
-   active branch
-   branch ownership by organization

## Shared Service Requirement

Branch validation must not be reimplemented ad hoc in controllers.

Use one central reusable context resolver such as:

-   `OrganizationBranchContextService`
-   or the existing context service extended for full active-branch
    enforcement

Suggested reusable model:

```java
public record ActiveContext(
    Long userId,
    Long organizationUserId,
    Long organizationId,
    Long branchId,
    String role
) {}
```

## Required Rollout Order

Implementation should proceed in this order:

1. Shared context and entity mappings
2. Snooker tables
3. Frame lifecycle
4. Ongoing/completed frame reporting
5. Leaderboard
6. Payment due calculation
7. Payment settlement
8. Manager earnings
9. Consumables and inventory
10. Kids play
11. Game activities
12. Tournaments
13. Customer feedback
14. Schedulers, WhatsApp and Brevo summaries
15. Frontend context refresh and cache cleanup

## Immediate Warning

Because `branch_id` is already `NOT NULL`, update **write paths first**
before report/query migrations.

Priority order for write paths:

1. Manual customer-related branch mappings, if affected
2. Start frame
3. Payments
4. Consumable orders
5. Kids play sessions
6. Game activity orders
7. Tournaments
8. Feedback

Any insert path that does not assign `branch_id` can fail immediately.

## Target Branch-Aware Behavior

### Snooker

-   Table lists must be branch-specific
-   Start frame must assign current branch
-   End frame must verify `frameId + branchId`
-   Ongoing/completed frames must filter by branch
-   Leaderboard must be branch-specific

### Dues and Settlement

-   Due calculation must become branch-specific
-   `user_dues` should be treated logically as `user_id + branch_id`
-   Show All Players, settlement, reminders and pending dues must share
    one branch-specific source of truth
-   Settlements must only affect records in the current branch
-   Payment history and settled-payments panels must filter by branch
-   Today’s Total Earnings must be recalculated branch-wise

### Consumables and Inventory

-   Only branch items should be shown
-   Orders and stock movement must be branch-aware
-   Reports must be branch-specific

### Kids Play and Game Activities

-   Sessions and activity orders must be created and settled by branch
-   Only current-branch sessions should appear in branch-specific manager
    views

### Tournaments and Feedback

-   Tournaments must belong to a branch
-   Tournament children should derive branch through tournament where
    appropriate
-   Feedback must be stored and reported by branch

### Schedulers

Schedulers must be explicitly reviewed to decide whether they are:

-   branch-specific, or
-   organization-wide

Examples:

-   Daily visit thank-you likely needs per-branch interaction logic
-   Payment due reminders may remain customer-facing and
    organization-wide in some cases
-   Birthday wishes are customer/organization scoped, not one message
    per branch

## Frontend Rule

Every branch-dependent frontend module must react to context switching.
When branch changes:

-   clear stale selections
-   cancel or ignore old requests
-   reload branch-specific data
-   refresh earnings, tables, frames, leaderboard, players, inventory,
    kids play, games, tournaments and similar modules

Use a shared Angular context service rather than scattered local-storage
reads where possible.

## Security Rule

Branch-sensitive repository lookups must use scoped methods, for
example:

```java
findByIdAndBranchId(...)
```

instead of global lookups like:

```java
findById(...)
```

for operational actions.

This applies to:

-   tables
-   frames
-   payments
-   orders
-   sessions
-   tournaments
-   feedback

## Suggested Delivery Batches

-   Batch 1: shared context, entity mappings, repository methods,
    snooker tables, start frame
-   Batch 2: end frame, ongoing/completed frames, leaderboard
-   Batch 3: due calculator, `user_dues`, settlement, payment history,
    earnings
-   Batch 4: consumables and inventory
-   Batch 5: kids play, games, activity orders
-   Batch 6: tournaments, feedback, schedulers, WhatsApp/Brevo summary
    behavior
-   Batch 7: frontend branch-refresh behavior, security regression
    tests, end-to-end verification

## Acceptance Direction

The migration phase should be considered complete only when:

-   every write persists current `branch_id`
-   every manager-facing read is branch-scoped
-   settlement is branch-scoped
-   earnings and reports are branch-scoped
-   branch switching refreshes all dependent modules
-   cross-branch data leakage is prevented
-   existing functionality remains intact
