# ADR 0005: Test database strategy

**Status:** Accepted

**Context.** The development machine had no Docker. Podman needed WSL2 and admin rights, which weren't available. So Testcontainers couldn't run locally.

**Decision.**
- Integration tests run the full Spring context against H2 in PostgreSQL mode, with the same Flyway migrations, plus an in-JVM Kafka broker (`@EmbeddedKafka`).
- CI runs the same suite against a real PostgreSQL 16 service container, configured through `TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD`.
- Migrations stick to SQL that both engines accept.

**Consequences.**
- Tests are fast and need no Docker.
- PostgreSQL-specific behaviour (for example `SKIP LOCKED` semantics) is only exercised in CI.
- Switching to Testcontainers later only means changing the test datasource configuration.
