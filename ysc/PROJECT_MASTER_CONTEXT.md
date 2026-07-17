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
