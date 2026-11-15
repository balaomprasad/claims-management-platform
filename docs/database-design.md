# Database Design

PostgreSQL 16, managed by Flyway (`backend/src/main/resources/db/migration`). Hibernate runs with `ddl-auto: validate`, so the entities must match the migrations exactly.

## Migrations

| Version | Purpose |
|---|---|
| V1 | `users` |
| V2 | `policies` |
| V3 | `claims`, `claim_status_history`, `claim_number_seq` |
| V4 | `notifications` |
| V5 | Roles CLAIMS_OFFICER and MANAGER, `claimant_profiles`, market and currency on policies and claims, the INFO_REQUESTED state, settlement fields, idempotent submission, plus the `claim_assignments`, `claim_assessments`, `claim_decisions` and `claim_information_requests` tables |
| V6 | `audit_logs`, `outbox_events` |
| V7 | `claim_documents` |

V5 changes the schema instead of editing V1–V4 because those were already pushed. That's the normal Flyway rule: never edit an applied migration.

## Entity–relationship diagram

```mermaid
erDiagram
    USERS ||--o| CLAIMANT_PROFILES : "has (claimants)"
    USERS ||--o{ POLICIES : holds
    USERS ||--o{ CLAIMS : files
    USERS |o--o{ CLAIMS : "assigned officer"
    POLICIES ||--o{ CLAIMS : "covers"
    CLAIMS ||--o{ CLAIM_STATUS_HISTORY : "status changes"
    CLAIMS ||--o{ CLAIM_ASSIGNMENTS : "assignment history"
    CLAIMS ||--o{ CLAIM_ASSESSMENTS : "officer notes"
    CLAIMS ||--o| CLAIM_DECISIONS : "approve / reject"
    CLAIMS ||--o{ CLAIM_INFORMATION_REQUESTS : "info requests"
    CLAIMS ||--o{ CLAIM_DOCUMENTS : "supporting documents"
    CLAIMS ||--o{ NOTIFICATIONS : "about"
    USERS ||--o{ NOTIFICATIONS : receives
    USERS |o--o{ AUDIT_LOGS : "actor"

    USERS {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar role "CLAIMANT | CLAIMS_OFFICER | MANAGER | ADMIN"
    }
    CLAIMANT_PROFILES {
        uuid user_id PK "FK users"
        varchar market
        varchar phone
        varchar address
    }
    POLICIES {
        uuid id PK
        varchar policy_number UK
        uuid holder_id FK
        varchar product_type
        varchar market
        varchar currency
        numeric coverage_amount
        numeric deductible
        date start_date
        date end_date
        varchar status
    }
    CLAIMS {
        uuid id PK
        varchar claim_number UK
        uuid policy_id FK
        uuid claimant_id FK
        uuid assigned_officer_id FK
        varchar claim_type
        varchar market
        varchar currency
        date incident_date
        varchar incident_location
        varchar incident_description
        numeric estimated_loss
        numeric approved_amount
        numeric settled_amount
        date settlement_date
        varchar settlement_reference
        varchar priority
        varchar status
        varchar submission_key "UK with claimant_id"
        timestamptz closed_at
        bigint version "optimistic lock"
    }
    CLAIM_STATUS_HISTORY {
        uuid id PK
        uuid claim_id FK
        varchar from_status
        varchar to_status
        uuid changed_by FK
        varchar comment
        timestamptz changed_at
    }
    CLAIM_ASSIGNMENTS {
        uuid id PK
        uuid claim_id FK
        uuid officer_id FK
        uuid assigned_by FK
        timestamptz assigned_at
    }
    CLAIM_ASSESSMENTS {
        uuid id PK
        uuid claim_id FK
        uuid officer_id FK
        varchar notes
        numeric recommended_amount
    }
    CLAIM_DECISIONS {
        uuid id PK
        uuid claim_id FK,UK
        varchar outcome "APPROVED | REJECTED"
        numeric amount
        varchar reason
        uuid decided_by FK
    }
    CLAIM_INFORMATION_REQUESTS {
        uuid id PK
        uuid claim_id FK
        uuid requested_by FK
        varchar message
        varchar response
        uuid responded_by FK
    }
    CLAIM_DOCUMENTS {
        uuid id PK
        uuid claim_id FK
        uuid uploaded_by FK
        varchar document_type
        varchar file_name
        varchar content_type
        bigint size_bytes
        varchar storage_key UK
        varchar checksum_sha256
    }
    NOTIFICATIONS {
        uuid id PK
        uuid user_id FK
        uuid claim_id FK
        uuid event_id "UK with user_id"
        varchar message
        boolean is_read
    }
    AUDIT_LOGS {
        uuid id PK
        uuid actor_id FK
        varchar action
        varchar entity_type
        varchar entity_id
        varchar details
        varchar correlation_id
    }
    OUTBOX_EVENTS {
        uuid id PK "= event id"
        varchar aggregate_type
        uuid aggregate_id
        varchar event_type
        varchar topic
        varchar payload
        timestamptz published_at
        int attempts
    }
```

## Design decisions

- **Claimant is a role plus a profile.** A claimant is a `users` row with role CLAIMANT and a 1:1 `claimant_profiles` row for contact details and home market. This avoids duplicating identity data.
- **The current state and its history are kept together.**
  - `claims.assigned_officer_id` points to the current officer, and `claim_assignments` keeps every assignment and reassignment.
  - `claims.approved_amount` holds the current approved figure that the liability totals use, and `claim_decisions` records who decided, when and why.
- **Market, currency and claim type are copied onto each claim.** They're stamped from the policy at submission. The claim keeps the terms it was filed under, and dashboard queries can group without joining `policies`.
- **Money.**
  - Amounts use `NUMERIC(15,2)` columns, mapped to `BigDecimal` with HALF_UP rounding.
  - Each amount sits next to a `currency` column.
  - Amounts in different currencies are never summed.
- **Time.** Timestamps are `TIMESTAMP WITH TIME ZONE`, with Hibernate's JDBC time zone set to UTC. Business dates (incident, settlement) are `DATE`.
- **Concurrency.** `claims.version` enables optimistic locking: two officers acting on the same claim at once get a 409, never a lost update.
- **Integrity.** Every foreign key is declared. CHECK constraints guard enums, positive amounts and a valid policy period. `ck_decisions_content` requires an amount for approvals and a reason for rejections.
- **Idempotency.**
  - `UNIQUE(claimant_id, submission_key)` stops duplicate claim submissions.
  - `UNIQUE(event_id, user_id)` stops duplicate notifications.
- **Documents.** Only metadata is stored in the database. The file content is held by the `FileStorage` adapter, keyed by a server-generated `storage_key`.
- **Sequences.** Claim numbers (`CLM-<year>-<6 digits>`) come from `claim_number_seq`, so they're unique even under concurrent submissions.

## Indexes and why

| Index | Supports |
|---|---|
| `uk_claims_number` | Lookup by claim number |
| `idx_claims_status_created (status, created_at DESC)` | Status-filtered, newest-first lists, and dashboard status counts |
| `idx_claims_market` | Market filters and per-market aggregation |
| `idx_claims_officer` | Officer queue ("assigned to me") and workload |
| `idx_claims_created_at` | Submission date-range filters and ageing |
| `idx_claims_claimant`, `idx_claims_policy` | Claimant's own claims; FK joins |
| `idx_history_claim (claim_id, changed_at)` | Claim timeline |
| `idx_assignments_claim`, `idx_assessments_claim`, `idx_info_requests_claim`, `idx_documents_claim` | Claim detail sections |
| `idx_notifications_user (user_id, is_read, created_at DESC)` | Unread-first notification list |
| `idx_audit_logs_entity (entity_type, entity_id, created_at)` | Audit trail of one entity |
| `idx_outbox_pending (published_at, created_at)` | Relay polling of unpublished events, oldest first |
| `idx_policies_holder`, `idx_policies_market` | Claimant's policies; market reporting |

## Test databases

Integration tests run Flyway against in-memory H2 in PostgreSQL mode, which keeps tests fast and Docker-free. CI runs the same suite against a real PostgreSQL 16 service container by setting `TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD`.

The migrations stick to SQL that both engines accept: no `gen_random_uuid()`, no partial indexes, and `VARCHAR` instead of `TEXT`.
