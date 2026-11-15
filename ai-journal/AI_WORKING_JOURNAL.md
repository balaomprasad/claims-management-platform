# ClaimSphere: AI Working Journal

This journal records how an AI coding assistant (Copilot SDK in VS Code) was used to build ClaimSphere, in the template from the project roadmap. Entries record what actually happened. Where the developer challenged or overrode the AI, that's stated explicitly; nothing here is invented.

---

## Session 1: Architecture and plan (2026-11-15)

### Prompt given
The roadmap's master planning prompt: act as a senior architect, read `AGENTS.md`, produce requirements, roles, domain model, schema, module boundaries, API, Kafka design, Angular architecture, security, testing and deployment strategy, risks and a file list. Wait for approval before implementing.

### AI recommendation
- A modular monolith: `common`, `security`, `user`, `policy`, `claim`, `notification`.
- JWT through Spring's OAuth2 resource server.
- PostgreSQL with Flyway.
- Kafka for claim events, published after commit.
- Command-style workflow endpoints and RFC 7807 errors.
- Angular standalone app with lazy routes.
- Scope limited to fit a 2–5 hour budget: no documents, no manager dashboard, no outbox.
- Two decisions left to the developer: the test database strategy and the Spring Boot version.

### Accepted
The modular monolith, JWT, PostgreSQL and Flyway, REST for synchronous operations and Kafka for asynchronous events.

### Decisions made by the developer
- Spring Boot **3.5** rather than 4.0.
- "Install everything required", including Podman if Docker isn't available.

### Environment findings
- No Docker, no Maven, no admin rights, WSL not installed, so Podman couldn't be installed.
- The developer chose the fallback: H2 (PostgreSQL mode) plus embedded Kafka locally, with real PostgreSQL in CI.
- `kubectl` was installed per-user; the Maven Wrapper replaced a system Maven.

---

## Session 2: First implementation (backend, frontend, infrastructure)

### Prompt given
Implement the approved plan phase by phase, building and testing after each phase.

### What was built and verified
- **Backend:** auth, policies, claim workflow (SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED → SETTLED, plus withdraw), status history, after-commit Kafka publishing, notifications. 54 tests passing.
- **Frontend:** Angular 21. Angular 22 needs Node ≥ 24.15, and the machine has 24.12. 8 tests passing, production build OK, plus an end-to-end browser run.
- **Infrastructure:** Dockerfiles, Compose, Kubernetes manifests, CI.

### Problems found and fixed during verification

| Problem | Fix |
|---|---|
| The production Angular build hung while inlining Google Fonts | Disabled font inlining |
| Critical-CSS inlining conflicts with the nginx CSP | Disabled it |
| Kubernetes `runAsNonRoot` rejects a named user | The image uses UID 10001 |
| The Kafka image misreads Kubernetes service-link environment variables | `enableServiceLinks: false` |
| Tests used local dates while the app uses UTC | Tests use `todayUtc()` |

---

## Session 3: Gap analysis against the roadmap

### Prompt given
The developer pasted the full roadmap and asked: "check if everything is there and satisfied".

### AI assessment
The core was in place. The approved plan had deliberately left out much of what the roadmap asks for:
- MANAGER and CLAIMS_OFFICER roles; INFO_REQUESTED; assessment notes; settlement details
- Market and currency; documents; the manager dashboard; an audit log; the outbox; correlation IDs
- Separate portals for each role
- Several docs, and this journal's location and format
- **No git commits at all**

### Overridden by the developer
The AI recommended a subset (about 2–3 extra hours). The developer chose **"add everything what I pasted says"**: every gap, including documents and the outbox. The developer also chose `ai-journal/` as the journal location.

### Challenged by the developer
- The AI started committing under the machine's global git identity (a corporate email). The developer stopped it: **use the personal GitHub account `balaomprasad`**.
- The AI set a repository-local identity using the account's GitHub noreply address. The global config was left untouched.
- The developer pushed the commits, since that needed their interactive sign-in.

---

## Session 4: Domain, workflow and APIs

### Accepted
- **Roles:** ADJUSTER renamed to CLAIMS_OFFICER; MANAGER added.
- **Workflow:** INFO_REQUESTED with the claimant's response; assessment notes; decisions; settlement amount, date and reference; reassignment by managers.
- **Claim data:** market, currency, claim type and location; claimant profiles.
- **APIs:** combined filters, lookup by claim number, editing claim details, `Idempotency-Key` for duplicate-safe submission.
- **Schema:** applied as a new Flyway migration (V5) instead of editing pushed migrations.

### Design choices the AI made (and why)
- **One `ClaimEvent` envelope with an `eventType`**, not six event classes. It's a single versioned contract and easier for consumers.
- **409 for invalid transitions** instead of the roadmap's suggested 400. The request is valid but conflicts with the claim's current state.
- **Claimant modelled as user + profile**, not a separate `claimant` entity, to avoid duplicating identity data.

### Verification
71 backend tests passing.

---

## Session 5: Audit log, correlation IDs, outbox, documents, dashboard

### Accepted
- **Audit:** an audit log written in the same transaction as the business change.
- **Correlation IDs:** a correlation-ID filter, carried into the MDC, outbox rows and Kafka headers.
- **Outbox:** a transactional outbox with a `SKIP LOCKED` relay, replacing the after-commit publisher, which could lose events.
- **Documents:** a `FileStorage` abstraction; file type validated from the file's contents; a cleanup that deletes the stored file on rollback.
- **Manager dashboard:** grouped database aggregation, with liability reported per currency.

### Bug found by a new test
- **What:** the consumer dead-letter test failed. DEBUG logs showed Spring Kafka 3.3's default dead-letter destination is `<topic>-dlt`.
- **Impact:** since the very first version, failed messages had gone to an auto-created topic instead of the declared `claimsphere.claim-events.DLT`.
- **Fix:** the error handler now sets the `.DLT` destination explicitly.

### Overridden (by the roadmap's own guidance)
The liability metrics never add amounts in different currencies. Exposure, approved liability and settled amount are reported per currency, and each metric's definition is included in the response. See [ADR 0003](../docs/adr/0003-currency-aware-liability.md).

### Verification
88 backend tests passing.

---

## Session 6: Frontend portals

### Accepted
- **Portals:** the claimant dashboard and report-incident form (documents upload after the claim is created); the officer workspace with assigned and unassigned queues; the manager dashboard.
- **Claim detail:** a role-aware action panel driven by typed commands.
- **Shared pieces:** loading, error and empty states.

### Problems found during the end-to-end browser run

| Problem | Fix |
|---|---|
| The claim page always requested `/decision` and showed an error snackbar for undecided claims (expected 404) | Only request it for decided claims |
| Managers couldn't list officers for reassignment: `/admin/users` is admin-only | Added `GET /users/officers` for managers and admins, with a test |
| A failed document upload after submission would have hidden that the claim had been created | Each upload's failure is caught separately; the user lands on the claim page with a message |

The browser automation had coordinate problems after viewport resizes. These were limits of the test tool, not app bugs: the element hit tests confirmed the buttons were on top. They were worked around with DOM clicks.

### Verification
- 12 frontend tests pass and the production build succeeds.
- The full roadmap end-to-end script (claimant → officer → claimant → officer → manager → notifications) passed in the browser. Screenshots are in `docs/screenshots/`.

---

## Session 7: Infrastructure and documentation

### Accepted
- Manifests moved to `infrastructure/kubernetes` with per-resource files.
- Uploads PVC, a single backend replica while storage is ReadWriteOnce, and a Compose healthcheck.
- CI validates the manifests with kubeconform.
- Documentation: README (all 18 roadmap sections, Mermaid diagrams, Minikube steps), architecture, API, database design, walkthrough, and six ADRs.

### Verification
13 rendered manifests pass `kubeconform -strict` against Kubernetes 1.30.

---

## Final reflection

**What I built.** A modular-monolith claims platform:
- Three role-specific portals.
- A complete officer workflow with information requests and settlement.
- Document handling.
- Currency-aware liability reporting.
- An audit trail and correlation IDs.
- Outbox-based Kafka events with an idempotent consumer, retries and a DLT.
- Docker, Compose and Kubernetes deployment assets, plus CI.

**What I prioritised.** Backend correctness and authorisation first: an explicit state machine, ownership checks, assigned-officer checks, optimistic locking and idempotency, each covered by tests. The UI came after.

**What I intentionally left out:**
- Refresh tokens and rate limiting
- FX conversion
- Object storage and virus scanning
- An admin UI
- Email and SMS

**What I would improve next:**
- Run CI against PostgreSQL and fix anything it finds.
- Object storage, and scaling the backend out.
- Upgrade to Spring Boot 4.
- Playwright end-to-end tests in CI.

**Verification gaps, stated honestly.** Docker images, `docker compose up` and a Minikube deployment were never run on the development machine, because no container runtime could be installed. They rely on CI and on a reviewer's environment.
