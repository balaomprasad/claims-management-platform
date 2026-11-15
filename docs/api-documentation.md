# API Documentation

All endpoints are under `/api/v1` and use JSON over HTTPS, except document upload and download.

The live OpenAPI contract is served by the backend:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw spec: `http://localhost:8080/v3/api-docs`

This page summarises the contract and the rules behind it.

## Conventions

| Topic | Convention |
|---|---|
| Authentication | `Authorization: Bearer <JWT>` from `POST /auth/login`. Tokens last 60 minutes. Roles are taken from the database at login and are never read from requests. |
| Correlation | Optional `X-Correlation-Id` header (`[A-Za-z0-9._-]{1,64}`). It's echoed back and propagated to logs, audit records, the outbox and Kafka headers. If missing or invalid, the server generates one. |
| Idempotency | `POST /claims` accepts `Idempotency-Key` (8–64 chars). Repeating a key returns the original claim with **200** instead of creating a new one (**201**). |
| Paging | `?page=0&size=20&sort=createdAt,desc`; `size` is capped at 100. The response shape is `{content, page, size, totalElements, totalPages}`. |
| Money | Decimal numbers with 2 places, always paired with a `currency` field. |
| Time | ISO-8601. Instants are UTC; business dates are `YYYY-MM-DD`. |

## Errors (RFC 7807)

```json
{ "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "Cannot transition from SUBMITTED to SETTLED",
  "errorCode": "INVALID_STATE_TRANSITION", "timestamp": "2026-11-15T09:24:37Z" }
```

| Status | Meaning | Example `errorCode` |
|---|---|---|
| 400 | Validation failed (`fieldErrors[]` included), malformed parameter | `VALIDATION_FAILED`, `INVALID_PARAMETER` |
| 401 | Missing or invalid token, bad credentials | `INVALID_CREDENTIALS` |
| 403 | Wrong role, or not the assigned officer | `ACCESS_DENIED` |
| 404 | Not found, **or owned by someone else** (prevents ID enumeration) | `NOT_FOUND` |
| 409 | Invalid transition, concurrent update, conflicting assignment, closed claim | `INVALID_STATE_TRANSITION`, `CONCURRENT_MODIFICATION`, `CLAIM_ALREADY_ASSIGNED`, `CLAIM_CLOSED` |
| 413 | Upload larger than the multipart limit | – |
| 422 | Business rule violated | `AMOUNT_EXCEEDS_COVERAGE`, `INCIDENT_OUTSIDE_POLICY_PERIOD`, `SETTLED_AMOUNT_EXCEEDS_APPROVED`, `UNSUPPORTED_DOCUMENT_TYPE` |

The 409 for an invalid transition is deliberate. The request is well-formed but conflicts with the claim's current state, which is the standard meaning of 409. The roadmap suggested 400 for this case.

## Roles

`CLAIMANT` (self-registered), `CLAIMS_OFFICER`, `MANAGER`, `ADMIN`. Role checks happen on controllers (`@PreAuthorize`). Ownership and assignment checks happen in services.

## Endpoints

### Auth and users

| Method | Path | Roles | Notes |
|---|---|---|---|
| POST | `/auth/register` | public | `{email, password, fullName, market, phone?}`. Always creates a CLAIMANT; a `role` field is ignored |
| POST | `/auth/login` | public | Returns `{accessToken, tokenType, expiresIn, user}` |
| GET | `/auth/me` | any | Current user |
| GET / PUT | `/claimants/me` | CLAIMANT | Own profile: market, phone, address |
| POST | `/admin/users` | ADMIN | `{email, password, fullName, role, market?}`; `market` is required for CLAIMANT |
| GET | `/admin/users?role=` | ADMIN | Paged |
| GET | `/users/officers` | MANAGER, ADMIN | Officers available for (re)assignment |

### Policies

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/policies` | any | Claimants see only their own |
| GET | `/policies/{id}` | any | Another claimant's policy returns 404 |
| POST | `/policies` | ADMIN | `{holderId, productType, market, currency?, coverageAmount, deductible, startDate, endDate}`; currency defaults to the market's local currency |

### Claims: queries

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/claims` | any | Filters: `status, market, claimType, priority, currency, assignedOfficerId, unassigned, assignedToMe, openOnly, incidentFrom/To, submittedFrom/To`. Claimants are always limited to their own claims |
| GET | `/claims/{id}` | any (scoped) | |
| GET | `/claims/by-number/{claimNumber}` | any (scoped) | |
| GET | `/claims/{id}/history` | any (scoped) | Status timeline with actor and comment |
| GET | `/claims/{id}/decision` | any (scoped) | 404 until the claim is decided |
| GET | `/claims/{id}/information-requests` | any (scoped) | Requests and the claimant's responses |
| GET | `/claims/{id}/assessments` | staff | Internal notes, never shown to claimants |
| GET | `/claims/{id}/assignments` | staff | Assignment history |
| GET | `/claims/summary` | staff | Counts by status |

### Claims: claimant commands

| Method | Path | Notes |
|---|---|---|
| POST | `/claims` | `{policyId, incidentDate, incidentLocation, incidentDescription, estimatedLoss}`. The policy must be the claimant's own and ACTIVE. The incident date must be inside the policy period and not in the future. The estimated loss must not exceed the coverage |
| PUT | `/claims/{id}` | Edit location, description or estimated loss; allowed only while SUBMITTED or INFO_REQUESTED |
| POST | `/claims/{id}/information` | `{response}`: moves INFO_REQUESTED to UNDER_REVIEW |
| POST | `/claims/{id}/withdraw` | `{comment?}`: allowed from SUBMITTED, UNDER_REVIEW or INFO_REQUESTED |

### Claims: officer and manager commands

| Method | Path | Roles | Rule |
|---|---|---|---|
| POST | `/claims/{id}/assign` | CLAIMS_OFFICER (self), MANAGER, ADMIN | `{officerId?, comment?}`. Officers can only self-assign an unassigned claim (otherwise 409 `CLAIM_ALREADY_ASSIGNED`). Managers and admins can also reassign a claim that's in review |
| POST | `/claims/{id}/assessments` | assigned officer | `{notes, recommendedAmount?}`; only while UNDER_REVIEW or INFO_REQUESTED |
| POST | `/claims/{id}/request-information` | assigned officer | `{message}`: UNDER_REVIEW to INFO_REQUESTED |
| POST | `/claims/{id}/approve` | assigned officer | `{approvedAmount, comment?}`; the approved amount can't exceed the estimated loss |
| POST | `/claims/{id}/reject` | assigned officer | `{reason}` is required |
| POST | `/claims/{id}/settle` | assigned officer, MANAGER | `{settledAmount, settlementDate?, settlementReference?}`; the settled amount can't exceed the approved amount, and the date can't be in the future |
| PUT | `/claims/{id}/priority` | CLAIMS_OFFICER, MANAGER | `{priority}`: LOW, NORMAL, HIGH or URGENT; open claims only |

### Documents

| Method | Path | Roles | Notes |
|---|---|---|---|
| POST | `/claims/{id}/documents` | owning CLAIMANT, CLAIMS_OFFICER, MANAGER | `multipart/form-data` with `file` and `documentType`. PDF, PNG or JPEG only, detected from the file's contents; at most 10 MB; the claim must be open |
| GET | `/claims/{id}/documents` | any (scoped) | Metadata, including a SHA-256 checksum |
| GET | `/claims/{id}/documents/{docId}/content` | any (scoped) | Always served as `Content-Disposition: attachment` with `nosniff` |

### Dashboard, notifications, audit

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/dashboard/manager` | MANAGER | Filters: `market, currency, submittedFrom, submittedTo, ageingDays`. See the metric definitions below |
| GET | `/notifications?unreadOnly=` | any | Own notifications |
| PATCH | `/notifications/{id}/read` | owner | 204 |
| GET | `/audit-logs` | MANAGER, ADMIN | Filters: `entityType, entityId, actorId, action` |

## Manager dashboard metrics

| Metric | Definition |
|---|---|
| `outstandingClaims` | Claims not yet closed: SUBMITTED, UNDER_REVIEW, INFO_REQUESTED or APPROVED |
| `estimatedExposure` (per currency) | Sum of `estimated_loss` on claims awaiting a decision (SUBMITTED, UNDER_REVIEW, INFO_REQUESTED) |
| `approvedLiability` (per currency) | Sum of `approved_amount` on APPROVED claims awaiting settlement |
| `settledAmount` (per currency) | Sum of `settled_amount` on SETTLED claims |
| `officerWorkload` | Per officer: open assigned claims; decided claims (approved, rejected or settled); settled claims |
| `processingTime.averageDays` | Mean of `closed_at - created_at` over SETTLED and REJECTED claims |
| `ageing` | Outstanding claims bucketed by days since submission, plus the count beyond `ageingDays` (default 30) |

Monetary metrics are always grouped by currency. No exchange-rate source is configured, so amounts in different currencies are never added together.

## Kafka event contract

| | |
|---|---|
| Topic | `claimsphere.claim-events`, keyed by `claimId` so events for one claim stay in order |
| Dead-letter topic | `claimsphere.claim-events.DLT` |
| Header | `X-Correlation-Id` |

```json
{
  "eventId": "9f0c…", "eventType": "CLAIM_SETTLED", "schemaVersion": 2,
  "occurredAt": "2026-11-15T09:24:37Z", "correlationId": "6e4a3802-…",
  "claimId": "0187…", "claimNumber": "CLM-2026-000006",
  "claimantId": "…", "assignedOfficerId": "…",
  "fromStatus": "APPROVED", "toStatus": "SETTLED",
  "amount": 2800.00, "currency": "SGD", "actorId": "…"
}
```

**Event types:**
- `CLAIM_SUBMITTED`
- `CLAIM_ASSIGNED`
- `CLAIM_INFORMATION_REQUESTED`
- `CLAIM_INFORMATION_PROVIDED`
- `CLAIM_APPROVED`
- `CLAIM_REJECTED`
- `CLAIM_SETTLED`
- `CLAIM_WITHDRAWN`

**What `amount` holds:** the settled amount for `CLAIM_SETTLED`, the approved amount for `CLAIM_APPROVED`, and the estimated loss for every other type.
