
# Project Instructions – ClaimSphere

## Project Overview
Build an enterprise-style Insurance Claims Management
Platform inspired by the Chubb APAC assessment.

## Technology
- Java 21
- Spring Boot
- Spring Security
- JWT authentication
- Spring Data JPA
- PostgreSQL
- Apache Kafka
- Flyway
- Maven
- Angular
- TypeScript
- Angular Material
- Docker
- Kubernetes

## Architecture
- Use a modular monolith backend.
- Follow layered architecture.
- Keep controllers thin.
- Put business logic in service classes.
- Use DTOs for API requests and responses.
- Use repositories for database operations.
- Use constructor-based dependency injection.
- Follow SOLID principles.
- Avoid unnecessary abstractions.

## Coding Standards
- Meaningful class and method names.
- No hardcoded credentials.
- Externalize configuration.
- Validate all incoming requests.
- Centralized exception handling.
- Use structured logging.
- Add meaningful JavaDoc where useful.
- Avoid duplicated business logic.
- Use transactions appropriately.

## Security
- Use JWT authentication.
- Implement role-based authorization.
- Validate ownership of claimant resources.
- Do not expose passwords or sensitive data.
- Never trust a user-supplied role.

## Database
- Use PostgreSQL.
- Use Flyway migrations.
- Add foreign keys and indexes.
- Use BigDecimal for monetary amounts.
- Store timestamps consistently in UTC.

## Testing
- Write JUnit 5 tests.
- Use Mockito for unit tests.
- Use integration tests for important API workflows.
- Test authorization and invalid state transitions.

## Frontend
- Use Angular standalone components.
- Use lazy-loaded routes.
- Use typed models and services.
- Implement route guards.
- Use HTTP interceptors.
- Keep business logic out of components.
- Make the UI responsive.

## Engineering Rules
- Never silently overwrite existing files.
- Explain architectural decisions before major changes.
- Build and test after every implementation phase.
- Do not claim a feature works without verifying it.
- Report incomplete functionality honestly.
- Update AI_WORKING_JOURNAL.md after each significant AI-assisted task.
- Keep documentation synchronized with the implementation.
