# ADR 0001: Modular monolith

**Status:** Accepted

**Context.** This is a take-home assessment with a 2–5 hour budget, built by one developer. The domain (claims, policies, documents, notifications) is a single bounded context with strongly consistent workflows.

**Decision.** Build one Spring Boot application, organised as package-per-module (`user`, `policy`, `claim`, `document`, `dashboard`, `audit`, `outbox`, `notification`), each layered internally.

- Modules talk to each other through services.
- The notification module depends only on the `ClaimEvent` contract delivered over Kafka.

**Consequences.**
- One deployment, one database, and local transactions for workflow consistency.
- No distributed-transaction or service-discovery overhead.
- Notifications, or document processing, can later be split out as separate services along the existing Kafka seam.
- Module boundaries are a convention, not enforced by tooling. Spring Modulith or ArchUnit rules could enforce them later.
