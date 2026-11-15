# ClaimSphere Architecture

## 1. Overview

ClaimSphere is a **modular monolith**: one deployable Spring Boot application, organised into modules with explicit boundaries. For a small team and a single bounded context, this keeps strong consistency and simple deployment. Kafka adds asynchronous, decoupled side effects (notifications) and leaves a clear seam for later extraction.

```
┌──────────────┐  HTTPS/JSON  ┌──────────────────────── Spring Boot ─────────────────────────┐
│ Angular SPA  │ ───────────▶ │ security ─▶ user │ policy │ claim ──(after commit)──┐        │
│ (nginx)      │              │                          │   │                      ▼        │
└──────────────┘              │                          │   │              KafkaTemplate    │
                              │   notification ◀── @KafkaListener ◀──── claimsphere.claim-events
                              └──────────────┬───────────────────────────────────────────────┘
                                             ▼
                                        PostgreSQL
```

## 2. Backend modules (`com.claimsphere`)

| Module | Responsibility | Depends on |
|---|---|---|
| `common` | Exceptions and the RFC 7807 handler, `PageResponse`, Clock/OpenAPI/Kafka config, auditing base entity | — |
| `security` | `SecurityConfig`, JWT issue/verify, `CurrentUser` resolution | common, user.domain |
| `user` | Registration, login, admin user management, demo seeder | common, security |
| `policy` | Policy creation and role-scoped queries | common, user |
| `claim` | Submission, workflow transitions, status history, event publishing | common, policy, user |
| `notification` | Kafka consumer, notification storage and API | common, `claim.event.ClaimEvent` (event contract only) |

Each module is layered as `domain → repository → service → web (+ dto)`.

- **Controllers are thin.** They validate input, resolve the current user, delegate, and return DTOs.
- **Cross-module calls go through services.** One exception: `notification` never calls `claim`; it only consumes events.
- **Claim services are split by intent:**
  - `ClaimService` handles role-scoped reads.
  - `ClaimWorkflowService` handles every state change.
  - Each transition is recorded in one place (`recordTransition`): persist, write history, raise event.
- **Domain invariants live on the `Claim` aggregate:** permitted transitions, and approve ≤ claimed amount.
- **Resource authorisation lives in `ClaimAccessPolicy`:** ownership checks and assigned-adjuster checks.

## 3. Data model (Flyway V1–V4)

| Table | Key columns / constraints |
|---|---|
| `users` | UUID PK, unique email, BCrypt hash, role CHECK |
| `policies` | FK holder → users, `NUMERIC(15,2)` coverage/deductible, CHECK period/amounts, index on holder |
| `claims` | FKs to policy, claimant, adjuster; sequence-backed `claim_number`; status CHECK; `version` (optimistic locking); indexes on claimant, policy, adjuster, (status, created_at) |
| `claim_status_history` | Immutable audit trail: from/to status, changed_by FK, comment, timestamp |
| `notifications` | `UNIQUE(event_id, user_id)`, the idempotency key for the consumer |

Conventions:
- Money is `BigDecimal`, scale 2, rounded HALF_UP.
- Timestamps are `Instant` stored as `TIMESTAMP WITH TIME ZONE`, with Hibernate's JDBC time zone set to UTC.
- An injectable UTC `Clock` makes time deterministic in tests.

## 4. API

The base path is `/api/v1`. The full contract is published as OpenAPI at `/v3/api-docs` and in Swagger UI.

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register` (always CLAIMANT), `POST /auth/login`, `GET /auth/me` |
| Users | `POST /admin/users`, `GET /admin/users?role=` (ADMIN) |
| Policies | `GET /policies`, `GET /policies/{id}` (scoped), `POST /policies` (ADMIN) |
| Claims | `POST /claims` (CLAIMANT), `GET /claims?status=`, `GET /claims/{id}`, `GET /claims/{id}/history`, `GET /claims/summary` |
| Workflow | `POST /claims/{id}/assign`, `/approve`, `/reject`, `/settle`, `/withdraw` |
| Notifications | `GET /notifications?unreadOnly=`, `PATCH /notifications/{id}/read` |

The workflow uses command endpoints, not a generic status update. Each command then has its own validated payload and authorisation rule.

**Error responses** use RFC 7807 `ProblemDetail`, plus `errorCode` and `timestamp`:

| Status | When |
|---|---|
| 400 | Validation failure (`fieldErrors[]` included) |
| 401 | Unauthenticated |
| 403 | Wrong role or not the assigned adjuster |
| 404 | Not found, or not owned (prevents ID enumeration) |
| 409 | Invalid transition or concurrent modification |
| 422 | Business rule violation |

## 5. Security

- **Token handling.**
  - Library: Spring Security's OAuth2 resource server, so there is no hand-written JWT filter.
  - Issuing: `NimbusJwtEncoder`.
  - Verifying: `NimbusJwtDecoder`, which checks signature, expiry and issuer.
- **Signing key.** HS256 with a key from `JWT_SECRET`. A key shorter than 32 bytes fails startup.
- **Roles.** Roles are read from the database at login and stored in the `roles` claim. Request-supplied roles are ignored, and registration always creates CLAIMANT.
- **Two-level authorisation:**
  1. `@PreAuthorize` role checks on controllers.
  2. Ownership and assignment checks in the service layer.
- **Passwords.** BCrypt hashing. Hashes never appear in any DTO or log.
- **Login failures.** The response is a generic 401, and the password hash is checked even for unknown emails so timing doesn't reveal which emails exist.
- **CSRF and CORS.** The API is stateless with no cookies, so CSRF protection is disabled. CORS is restricted to the configured origins.
- **Frontend.** The token is kept in `sessionStorage`, the session logs out when the token expires, and nginx sends a strict CSP plus security headers.

## 6. Kafka events

**Topic `claimsphere.claim-events`:**
- Keyed by `claimId`, so events for one claim stay in order.
- A dead-letter topic `.DLT` sits alongside it.

**Event payload** (`ClaimEvent`, schemaVersion 1):
- Fields: `eventId`, `eventType`, `occurredAt`, `claimId`, `claimNumber`, `claimantId`, `assignedAdjusterId`, `fromStatus`, `toStatus`, `amount`, `actorId`.
- Statuses are plain strings, so consumers don't depend on claim internals.

**Producer.**
- The workflow service raises an application event inside the transaction.
- `ClaimEventPublisher` handles it with `@TransactionalEventListener(AFTER_COMMIT)`, so events for rolled-back changes are never published.
- The producer uses `acks=all` and idempotence.

**Consumer.** `ClaimEventListener` runs in group `claimsphere-notifications`.
- It is idempotent through the `(event_id, user_id)` unique key.
- `DefaultErrorHandler` makes 3 attempts, then publishes the message to the DLT.

**Trade-off.** There is no transactional outbox, which keeps the solution within the time-box. If Kafka is unavailable at commit time, an event can be lost; the failure is logged with IDs. An outbox table plus a relay is the recommended next step.

## 7. Frontend

- **Platform.** Angular 21: standalone components, zoneless change detection, signals and lazy-loaded routes.
- **`core/`** has no UI:
  - Typed models.
  - HTTP services.
  - `authInterceptor`, which adds the Bearer token to `/api` calls only.
  - `errorInterceptor`, which logs out on 401 and shows ProblemDetail messages in a snackbar.
  - `authGuard` and `roleGuard`.
  - `claim-actions.ts`, a pure function that decides which workflow actions the UI offers.
- **`layout/shell`.** A responsive toolbar and sidenav, with nav items filtered by role.
- **`features/`.**
  - auth: login, register
  - claims: list with status filter, summary and paging; form; detail with action panel and history
  - policies
  - notifications

The frontend is a convenience layer: the backend re-validates every rule.

## 8. Deployment

- **Docker.**
  - Both images are multi-stage builds and run as non-root users.
  - Backend: Temurin 21 JRE, UID 10001.
  - Frontend: `nginx-unprivileged`, which proxies `/api` to `${BACKEND_URL}`.
- **Compose.**
  - Services: PostgreSQL 16, single-node Kafka (KRaft), backend and frontend.
  - Startup is ordered by healthchecks.
  - Kafka exposes `localhost:9094` for tools running on the host.
- **Kubernetes** (`kubectl apply -k k8s`):
  - Namespace and ConfigMap; the Secret is generated from a git-ignored `secrets.env`.
  - PostgreSQL and Kafka StatefulSets with PVCs.
  - Backend Deployment with 2 replicas and startup/liveness/readiness probes on Actuator.
  - Frontend Deployment and Ingress.
  - `enableServiceLinks: false` stops Kubernetes from injecting `KAFKA_*` variables that the Kafka image would misread as broker configuration.
- **CI** (`.github/workflows/ci.yml`):
  1. Backend `mvnw verify` against a PostgreSQL service container.
  2. Frontend tests and production build.
  3. Docker image builds and Kustomize rendering.

## 9. Testing strategy

| Level | Tooling | Focus |
|---|---|---|
| Domain | JUnit 5 (parameterised) | State machine transition matrix |
| Service | JUnit 5 + Mockito | Business rules, authorisation, history and event emission, consumer idempotency |
| API integration | `@SpringBootTest` + MockMvc + Flyway + embedded Kafka | Full workflows, 401/403/404/409/422 paths |
| Messaging integration | Embedded Kafka (KRaft) + Awaitility | Commit → publish → consume → notification |
| Frontend | Vitest + TestBed | Auth session, role guard, workflow action rules |

## 10. Key decisions

| Decision | Rationale |
|---|---|
| Modular monolith | Fits the scope and team size; module boundaries keep later extraction possible |
| Spring Boot 3.5.x | Mature APIs, low risk for the time-box. Initializr now defaults to 4.x, so upgrading is a follow-up |
| OAuth2 resource server instead of a custom JWT filter | Less security-sensitive custom code |
| Command endpoints for the workflow | Explicit validation and authorisation per transition |
| 404 for non-owned resources | Prevents enumeration of other users' claims and policies |
| After-commit publishing instead of an outbox | Simple and correct for rollbacks; the outbox is documented future work |
| H2 (PostgreSQL mode) for local tests, PostgreSQL in CI | Docker was unavailable in the development environment; CI covers the real database |
| Angular 21 instead of 22 | Angular 22 needs Node ≥ 24.15; the dev machine has 24.12. Angular 21 is still in LTS |
