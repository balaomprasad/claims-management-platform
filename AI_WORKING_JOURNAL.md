# AI Working Journal

This journal records significant AI-assisted work on ClaimSphere, as required by `AGENTS.md`. Each entry covers what was done, the decisions made, how the work was verified, and what remains open.

---

## 2026-11-15 — Session 1: Planning and initial implementation

**Assistant:** AI coding assistant (Copilot SDK in VS Code), acting as architect and engineering lead.

### 1. Requirements analysis and plan (approved by the developer)

- Produced a plan covering functional and non-functional requirements, role permissions, domain model, schema, module boundaries, REST contracts, Kafka design, Angular architecture, security, testing and deployment, plus a risk register and the full file list.
- The developer approved the plan and made these decisions:
  - **Spring Boot 3.5.**
  - **Install missing tooling.**
  - **Use Podman if Docker is unavailable.**
- **Environment findings:**
  - No Docker or Maven, and no admin rights. WSL is not installed.
  - Podman therefore couldn't be installed.
  - The developer chose the fallback: H2 (PostgreSQL mode) and embedded Kafka locally, with real PostgreSQL in CI.
  - kubectl was installed per-user, and the Maven Wrapper is used instead of a system Maven.

### 2. Backend (Phases 0–4)

- Built a modular monolith with six modules: `common`, `security`, `user`, `policy`, `claim` and `notification`.
- Wrote Flyway migrations V1–V4 with foreign keys, CHECK constraints, indexes, a claim-number sequence and optimistic locking.
- Implemented JWT security with the Spring OAuth2 resource server (Nimbus, HS256). The secret comes from the environment and is validated at startup.
- Implemented the claim workflow as a state machine with command endpoints, an immutable status history and after-commit Kafka publishing.
- Implemented the notification consumer: idempotent per (event, user), with DLT error handling.
- Added RFC 7807 error handling, OpenAPI, structured ECS logging, and a `demo` profile seeder whose passwords come from env vars.
- **Verification:** `./mvnw verify` passes 54/54 tests (unit, MockMvc integration, embedded-Kafka integration).

### 3. Infrastructure (Phase 5)

- Wrote multi-stage, non-root Dockerfiles; Docker Compose with PostgreSQL, KRaft Kafka, backend and frontend; Kubernetes manifests via Kustomize, with secrets generated from a git-ignored file; and a GitHub Actions CI workflow.
- **Verification:**
  - `kubectl kustomize k8s` renders all 12 resources.
  - Docker images, Compose and cluster deployment are **not verified**, because Docker was unavailable.

### 4. Frontend (Phase 6)

- Angular 22 requires Node ≥ 24.15, and the machine has 24.12, so the app uses **Angular 21.2** (still LTS) instead.
- Built a standalone, zoneless app with lazy routes, Material, functional interceptors and guards, and a pure `claim-actions` rule function.
- **Verification:**
  - `ng test`: 8/8 pass. `ng build` (production) succeeds.
  - An end-to-end browser run against the Docker-free stack (`spring-boot:test-run` plus `ng serve`):
    1. The claimant logs in and files a claim.
    2. The adjuster self-assigns and approves it.
    3. The admin settles it.
    4. The claimant sees the full history and four Kafka-driven notifications.

### 5. Issues found and fixed during the session

| Issue | Fix |
|---|---|
| Angular production build hung while inlining Google Fonts (corporate network/proxy) | Disabled font inlining (`optimization.fonts: false`) |
| Angular critical-CSS inlining uses an inline `onload` handler, which the nginx CSP blocks | Disabled `inlineCritical` |
| Kubernetes `runAsNonRoot` rejects images with a named (non-numeric) user | Backend image now uses UID 10001 |
| Kafka image misreads Kubernetes service-link env vars (`KAFKA_PORT`) as broker config | Set `enableServiceLinks: false` |
| Tests used the local-time-zone date while the app uses UTC (flaky between 00:00 and 05:30 IST) | Tests use `todayUtc()` |

### 6. Open items / follow-ups

- Run CI once to confirm the tests pass on real PostgreSQL and the Docker images build.
- Spring Boot 3.5 is no longer offered by Initializr; plan an upgrade to Boot 4.x.
- Add a transactional outbox for guaranteed event delivery.
- Add refresh tokens and login rate limiting.
- Add an admin UI for creating users and policies (currently API only).
