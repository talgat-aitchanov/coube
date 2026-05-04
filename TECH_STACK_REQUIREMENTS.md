# Tech Stack Requirements

## Core Principles

The application must be **scaleable, robust, and resilient**, and follow these engineering principles:

- **SOLID** — Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion
- **DRY** — Don't Repeat Yourself
- **YAGNI** — You Aren't Gonna Need It (no speculative abstractions)
- **TDD** — Test-Driven Development (unit tests for business logic, integration tests for slices, e2e for happy paths)

### Architecture Style

**Layered architecture** with strict separation between layers:

- DTOs (HTTP boundary) ≠ Domain models ≠ JPA entities
- Mappers (MapStruct) at every layer boundary
- JPA / `@Entity` confined to the persistence layer
- Domain models in `model/` package have **no framework imports** (verified by ArchUnit)

This is *Layered MVC with clean separation*, not strict Clean Architecture (services have `@Service`).
For details and rationale, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and
[`docs/adr/0001-layered-mvc.md`](docs/adr/0001-layered-mvc.md).

---

## Technology Stack

| Layer             | Technology                                                        |
|-------------------|-------------------------------------------------------------------|
| Language          | **Java 17** (LTS)                                                 |
| Framework         | Spring Boot 3.4.x                                                 |
| Web               | Spring Web (REST)                                                 |
| Validation        | Jakarta Bean Validation                                           |
| Data Access       | Spring Data JPA + Hibernate                                       |
| Database          | PostgreSQL 16                                                     |
| Migrations        | Flyway                                                            |
| Mapping           | MapStruct                                                         |
| Boilerplate       | Lombok (entities, services) — records preferred for value objects |
| Containerization  | Docker / Docker Compose                                           |
| API Documentation | OpenAPI 3 (springdoc-openapi)                                     |
| Observability     | Spring Boot Actuator + Micrometer + `@Timed` AOP                 |
| Build             | Gradle (Groovy DSL)                                               |
| Coverage          | JaCoCo (XML + HTML reports, merged across all test suites)        |

### Language feature usage

- **Records** — DTOs, domain models, value objects (immutable by default)
- **Lombok** — JPA entities (`@Getter`/`@Setter`/`@NoArgsConstructor` required by JPA), services (
  `@RequiredArgsConstructor`, `@Slf4j`)
- **MapStruct** — type-safe compile-time mapping at layer boundaries

---

## Infrastructure

### Local Development

- PostgreSQL runs in Docker via `docker-compose.yml`
- App runs locally (`./gradlew bootRun --args='--spring.profiles.active=local'`) or via the project Dockerfile
- `application-local.yml` profile supplies local overrides

### End-to-End Tests

- PostgreSQL via Testcontainers (no Docker Compose dependency in CI)
- Tests boot full Spring context against an ephemeral DB
- No mocking of infrastructure in e2e tests

### Production

- Multi-stage Dockerfile produces a non-root JRE image
- All configuration via environment variables (`SPRING_DATASOURCE_*`, `SPRING_PROFILES_ACTIVE=prod`, etc.)
- Swagger UI disabled in `prod` profile

---

## API Documentation

- OpenAPI 3 spec auto-generated from controller annotations (`@Operation`, `@ApiResponse`, `@Schema`)
- Swagger UI at `/swagger-ui.html` (local/dev only)
- Spec exportable as `/v3/api-docs` (JSON) or `/v3/api-docs.yaml`
- Error responses follow **RFC 7807 (ProblemDetail)**

---

## Testing Strategy

| Level        | Scope                          | Tools                                          |
|--------------|--------------------------------|------------------------------------------------|
| Unit         | Service formula logic, mappers | JUnit 5, AssertJ, Mockito                      |
| Integration  | Web slice, persistence slice   | `@WebMvcTest`, `@DataJpaTest`, Testcontainers  |
| E2E          | Full HTTP-to-DB flow           | `@SpringBootTest`, RestAssured, Testcontainers |
| Architecture | Layer boundaries               | ArchUnit                                       |
| Coverage     | All suites merged              | JaCoCo                                         |

Coverage target: business logic 100%, overall ≥ 80%.

---

## Quality Gates

- All tests green before merge
- ArchUnit: no `org.springframework.*` or `jakarta.persistence.*` imports inside `com.coube.delivery.model.*`
- ArchUnit: `entity/` is reachable only from `repository/`, `mapper/`, and `service/` packages
- ArchUnit: `controller/` does not depend on `entity/` or `repository/`
- OpenAPI spec valid for every endpoint (Springdoc validation enabled)
- Docker Compose starts cleanly with `docker compose up`
- App image builds and runs (`docker build -t coube-delivery .`)
