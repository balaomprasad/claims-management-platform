# ADR 0006: Framework versions

**Status:** Accepted

**Decisions:**
- **Spring Boot 3.5.16.** Chosen by the developer for its mature APIs and lower risk within the time-box. Spring Initializr now offers only 4.x, so upgrading to Boot 4 (Spring Security 7, Jackson 3) is a planned follow-up.
- **Angular 21.2.** Angular 22 requires Node ≥ 24.15 and the machine has 24.12. Angular 21 is still supported and uses the same standalone, signals and zoneless APIs.
- **Java 21 LTS** and **Kafka 3.9 in KRaft mode** (no ZooKeeper).

**Consequences.** All versions are pinned in `pom.xml` and `package.json`. CI uses Node 22 LTS for the frontend build.
