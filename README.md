# ClaimSphere — Insurance Claims Management Platform

ClaimSphere is a full-stack insurance claims platform. Claimants file claims against their policies, adjusters review and decide them, and admins settle approved claims. Every lifecycle change is audited and published to Kafka, which drives in-app notifications.

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security (JWT, OAuth2 resource server), Spring Data JPA, Flyway, Spring Kafka |
| Database | PostgreSQL 16 (H2 in PostgreSQL mode for local tests) |
| Messaging | Apache Kafka 3.9 (KRaft) |
| Frontend | Angular 21 (standalone, zoneless, signals), Angular Material, Vitest |
| Delivery | Docker, Docker Compose, Kubernetes (Kustomize), GitHub Actions |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design, and [AI_WORKING_JOURNAL.md](AI_WORKING_JOURNAL.md) for the AI-assisted development log.

---

## Repository layout

```
backend/     Spring Boot modular monolith (common, security, user, policy, claim, notification)
frontend/    Angular SPA
k8s/         Kubernetes manifests (kubectl apply -k k8s)
docs/        Architecture documentation
.github/     CI pipeline
docker-compose.yml
```

## Claim lifecycle

```
SUBMITTED ──assign──▶ UNDER_REVIEW ──approve──▶ APPROVED ──settle──▶ SETTLED
    │                     │  └──────reject────▶ REJECTED
    └──withdraw──▶ WITHDRAWN ◀──withdraw──┘
```

| Role | Can do |
|---|---|
| CLAIMANT (self-registers) | View own policies and claims, submit claims, withdraw own open claims |
| ADJUSTER | View all claims, self-assign submitted claims, approve/reject claims assigned to them |
| ADMIN | Create users and policies, assign claims to adjusters, settle approved claims |

---

## Running locally

### Option A: Docker Compose (full stack)

```bash
cp .env.example .env        # then replace every value
docker compose up --build
```

- UI: http://localhost:4200
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

With `SPRING_PROFILES_ACTIVE=demo`, the backend seeds three users and two policies. Their passwords are the `DEMO_*_PASSWORD` values from your `.env`.

| Email | Role |
|---|---|
| `admin@claimsphere.local` | ADMIN |
| `adjuster@claimsphere.local` | ADJUSTER |
| `claimant@claimsphere.local` | CLAIMANT |

### Option B: Without Docker (H2 + embedded Kafka)

Use this to run the real application without any containers. The launcher lives on the test classpath and is never packaged.

```powershell
cd backend
$env:JWT_SECRET = "<at least 32 random characters>"
$env:DEMO_ADMIN_PASSWORD = "<choose>"; $env:DEMO_ADJUSTER_PASSWORD = "<choose>"; $env:DEMO_CLAIMANT_PASSWORD = "<choose>"
./mvnw spring-boot:test-run
```

```bash
cd frontend
npm ci
npx ng serve        # http://localhost:4200, proxies /api to :8080
```

### Option C: Backend against your own PostgreSQL and Kafka

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` and `KAFKA_BOOTSTRAP_SERVERS`, then run `./mvnw spring-boot:run`.

---

## Configuration

All configuration comes from environment variables. Secrets have no defaults, and the application refuses to start without them.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_URL` | | `jdbc:postgresql://localhost:5432/claimsphere` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | ✅ | — | Database credentials |
| `JWT_SECRET` | ✅ | — | HS256 signing key, ≥ 32 bytes |
| `JWT_EXPIRATION` | | `PT60M` | Access token lifetime |
| `KAFKA_BOOTSTRAP_SERVERS` | | `localhost:9094` | Matches the host listener in Compose |
| `CORS_ALLOWED_ORIGINS` | | `http://localhost:4200` | Comma-separated list |
| `SPRING_PROFILES_ACTIVE` | | — | `demo` enables demo data seeding |
| `DEMO_ADMIN_PASSWORD`, `DEMO_ADJUSTER_PASSWORD`, `DEMO_CLAIMANT_PASSWORD` | demo only | — | Seeded user passwords |
| `LOG_FORMAT` | | `ecs` | Structured JSON console logs |

---

## Testing

```bash
cd backend && ./mvnw verify                  # 54 tests: unit + API integration + Kafka integration
cd frontend && npx ng test --watch=false     # 8 tests (Vitest)
```

**Backend integration tests** run the full Spring context with MockMvc, Flyway-migrated H2 (PostgreSQL mode) and an in-JVM Kafka broker.

To run them against real PostgreSQL, set `TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD`. CI does this with a PostgreSQL service container.

| Suite | Covers |
|---|---|
| `ClaimStatusTest` | The full transition matrix |
| `ClaimWorkflowServiceTest` | Business rules: coverage, dates, assigned-adjuster checks, approve ≤ claimed |
| `ClaimWorkflowIntegrationTest` | Submit → assign → approve → settle over HTTP, with history |
| `ClaimAuthorizationIntegrationTest` | Ownership (404), roles (403), invalid transitions (409) |
| `AuthControllerIntegrationTest` | Register ignores a supplied role, login, 401s, admin-only user creation |
| `ClaimEventKafkaIntegrationTest` | Transition → Kafka → consumer → notifications API |

---

## Kubernetes

```bash
cp k8s/secrets.env.example k8s/secrets.env   # replace values; file is git-ignored
docker build -t claimsphere-backend:latest backend
docker build -t claimsphere-frontend:latest frontend
kubectl apply -k k8s
```

The Ingress host is `claimsphere.local`. PostgreSQL and Kafka run as single-replica StatefulSets for demo purposes. Production should use managed services.

---

## Verification status

**Verified in development:**
- Backend `mvnw verify`: 54/54 tests pass on H2 with embedded Kafka.
- Frontend unit tests: 8/8 pass, and the production build succeeds.
- A manual end-to-end browser run against the Docker-free stack (Option B). It covered register/login, filing a claim, assign, approve, settle, the history timeline and Kafka-driven notifications.
- `kubectl kustomize k8s` renders all manifests.

**Not verified in the development environment** (Docker was unavailable):
- Building the Docker images.
- Running `docker compose up`.
- Deploying to a Kubernetes cluster.
- Running the test suite against real PostgreSQL. The CI pipeline is set up to cover this, but it has not been run yet.

## Known limitations

- No transactional outbox: if Kafka is down at commit time, an event can be lost. Failures are logged with claim and event IDs.
- No refresh tokens or token revocation. Access tokens last 60 minutes and are kept in `sessionStorage`.
- No rate limiting on login.
- No document uploads, payments, email or SMS.
- No admin UI for creating users or policies. Use the API or Swagger UI for those.
- Approval caps the amount at the claimed amount; deductible handling is left to the adjuster.
