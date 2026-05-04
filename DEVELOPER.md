# Coube — Delivery Price Calculation API

A Spring Boot REST service that computes delivery prices from distance, weight, cargo type, and urgency.
Each calculation is persisted as an audit record in PostgreSQL.

**Stack:** Java 17 · Spring Boot 3.4 · Gradle (Groovy DSL) · PostgreSQL 16 · Flyway · MapStruct · Lombok · Docker

---

## Quick Start

### Prerequisites

- Java 17 (`java -version`)
- Docker (for PostgreSQL and the app image)

### Run locally

```bash
docker compose up -d                                              # start PostgreSQL
./gradlew bootRun --args='--spring.profiles.active=local'         # start the app on :8080
```

Open: http://localhost:8080/swagger-ui.html

### Test the endpoint

```bash
curl -X POST http://localhost:8080/api/v1/delivery/calculate \
  -H "Content-Type: application/json" \
  -d '{"distanceKm": 450, "weightTon": 12.5, "cargoType": "FRAGILE", "isUrgent": true}'
```

Expected response:

```json
{
  "basePrice": 45000.00,
  "urgentSurcharge": 9000.00,
  "cargoTypeSurcharge": 4500.00,
  "totalPrice": 58500.00,
  "currency": "KZT"
}
```

---

## API

### `POST /api/v1/delivery/calculate`

| Field        | Type    | Range / Values                     |
|--------------|---------|------------------------------------|
| `distanceKm` | number  | 1 – 5000                           |
| `weightTon`  | number  | 0.1 – 120                          |
| `cargoType`  | string  | `FRAGILE`, `OVERSIZED`, `STANDARD` |
| `isUrgent`   | boolean | required                           |

Validation failures return **400** with an RFC 7807 `ProblemDetail` body.

### Formula

```
basePrice          = distanceKm × weightTon × baseRate         (baseRate = 8 KZT/km/ton)
urgentSurcharge    = isUrgent ? basePrice × 0.20 : 0
cargoTypeSurcharge = basePrice × { FRAGILE 0.10 | OVERSIZED 0.25 | STANDARD 0 }
totalPrice         = basePrice + urgentSurcharge + cargoTypeSurcharge
currency           = KZT (hardcoded; tariff rates are currency-agnostic multipliers)
```

Tariff rates live in PostgreSQL (`tariff_config` + `cargo_surcharge` tables). The initial values are
seeded inline by the single Flyway migration `V1__create_schema.sql`.

---

## Build & Test

```bash
./gradlew test                   # unit tests only (no Spring context, no Docker)
./gradlew integrationTest        # slice tests (@WebMvcTest, @DataJpaTest + Testcontainers)
./gradlew e2eTest                # full-stack tests (Testcontainers Postgres)
./gradlew check                  # all three + JaCoCo coverage report
docker build -t coube-delivery . # build the Docker image
```

Coverage reports are written to `build/reports/jacoco/jacocoCoverageReport/`:
- `html/index.html` — browsable HTML report
- `jacocoCoverageReport.xml` — machine-readable XML (CI / SonarQube)

---

## Profiles

| Profile | Purpose    | DB                               |
|---------|------------|----------------------------------|
| `local` | Local dev  | Docker Compose Postgres          |
| `test`  | Tests      | Testcontainers Postgres          |
| `prod`  | Production | Env vars (`SPRING_DATASOURCE_*`) |

---

## Project Layout

```
coube/
├── docs/                                Architecture, ADRs, exploration
├── src/main/java/com/coube/delivery/
│   ├── controller/                      HTTP layer (DTOs + constraints in subpackage)
│   ├── service/                         Business logic
│   ├── model/                           Domain records (no framework imports)
│   ├── entity/                          JPA entities (Lombok)
│   ├── repository/                      Spring Data JPA
│   ├── mapper/                          MapStruct mappers
│   ├── filter/                          Servlet filters (correlation ID, request logging)
│   ├── config/                          Spring @Configuration beans (metrics, OpenAPI)
│   └── exception/                       Global error handler + custom exceptions
├── src/main/resources/
│   ├── application.yml + application-{local,test,prod}.yml
│   └── db/migration/                    Flyway SQL migrations (single V1 file)
├── src/test/java/com/coube/delivery/    Unit + integration tests
├── src/e2eTest/java/com/coube/delivery/ E2E tests (separate source set)
├── docker-compose.yml                   Local Postgres
├── Dockerfile                           Multi-stage app image
├── build.gradle                         Gradle (Groovy DSL)
└── settings.gradle
```

---

## Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — current architecture
- [`docs/adr/`](docs/adr/) — architecture decision records
- [`docs/exploration/`](docs/exploration/) — superseded design exploration
- [`TECH_STACK_REQUIREMENTS.md`](TECH_STACK_REQUIREMENTS.md) — engineering principles & quality gates

---

## Deferred Features (planned)

These are intentionally **not** implemented in the current scope. They are documented here so the
team can reason about the future shape of the system and avoid premature design.

### 1. Admin REST endpoints — tariff CRUD

- `GET /api/admin/tariffs` — list active + history
- `GET /api/admin/tariffs/active`
- `POST /api/admin/tariffs` — create new active tariff (closes previous via `effective_to`)

The DB schema (`tariff_config`, `cargo_surcharge`) is already shaped for this — insert-only history,
partial unique index ensures only one active tariff at a time. **No schema changes will be needed.**

### 2. React SPA admin UI

A separate frontend will consume the admin REST endpoints. CORS will need to be configured
when the admin endpoints are added.

### 3. API key authentication

The admin endpoints will be protected with `X-API-Key`-header authentication via a
`OncePerRequestFilter`. Public `/api/v1/delivery/**` endpoints remain unauthenticated.

### 4. Tariff caching with Redis

With multiple replicas, an in-process Caffeine cache would diverge across instances when the
admin updates the tariff. The plan is to add `spring-boot-starter-data-redis` and switch
`@Cacheable` to a Redis backend, with cache eviction triggered by every admin write.

### 5. Tariff history queries

`GET /api/admin/calculations?from=...&to=...` — query persisted calculation audit records.
The `delivery_calculation` table already has an index on `calculated_at DESC` for this.

# Coube — Admin UI Extension Plan

This document describes what needs to change when the **admin tariff management UI** is added.
The current system exposes one public endpoint (`POST /api/v1/delivery/calculate`) and manages
all tariff data through Flyway seed data. The admin UI will add live tariff CRUD.

For the current developer quick-start and API reference, see [`DEVELOPER.md`](DEVELOPER.md).

---

## What Exists Today

- `tariff_config` — insert-only history table; `effective_to IS NULL` marks the one active row
- `cargo_surcharge` — per-cargo-type rate rows linked to a tariff
- A partial unique index enforces that **only one active tariff can exist at a time**
- `DeliveryCalculationService` reads the active tariff on every request (no cache)

---

## Changes Required

### 1. Admin REST Endpoints

New controller: `AdminTariffController` under `/api/admin/tariffs`.

| Method | Path                      | What                                              |
|--------|---------------------------|---------------------------------------------------|
| GET    | `/api/admin/tariffs`      | List all tariffs (active + history), paginated    |
| GET    | `/api/admin/tariffs/active` | Return the single active tariff                 |
| POST   | `/api/admin/tariffs`      | Create a new active tariff (closes the previous)  |

No DELETE or PUT — the schema is intentionally insert-only (full history preserved).

New DTOs: `CreateTariffRequest` (baseRate, urgentRate, surcharges map), `TariffResponse`.

---

### 2. Authentication

A new `ApiKeyAuthFilter` (`OncePerRequestFilter`) must guard all `/api/admin/**` paths.
The key is loaded from an environment variable (`ADMIN_API_KEY`). Public endpoints
(`/api/v1/delivery/**`, `/actuator/**`, `/swagger-ui/**`) remain unauthenticated.

---

### 3. Consistency — Pessimistic Lock on Tariff Write (critical)

**The problem.**

Creating a new active tariff involves two writes in sequence:

```
1. UPDATE tariff_config SET effective_to = NOW() WHERE effective_to IS NULL
2. INSERT INTO tariff_config (base_rate, urgent_rate, ...) VALUES (...)
3. INSERT INTO cargo_surcharge (...) VALUES (...) × 3
```

Without coordination, two concurrent admin requests could both read the same active tariff,
both execute step 1 (the second silently matches zero rows after the first commits), and
then both try to insert a new active row. The partial unique index would reject the second
insert — but only at commit time, producing an unhandled constraint violation instead of a
meaningful API error. Worse, under `READ COMMITTED` isolation the second transaction might
not even see that step 1 was already done by the first.

**The fix: pessimistic `SELECT FOR UPDATE`.**

Before any mutation, the service must lock the active row:

```java
@Transactional
public TariffRates createTariff(CreateTariffRequest request) {
    // Acquires an exclusive row lock. Any concurrent writer blocks here until
    // this transaction commits or rolls back. Deadlocks are impossible because
    // there is always exactly one active row (enforced by the partial index).
    TariffConfigEntity current = tariffRepository.findActiveTariffForUpdate()
            .orElseThrow(() -> new TariffNotFoundException("No active tariff to supersede"));

    current.setEffectiveTo(Instant.now());
    tariffRepository.save(current);

    TariffConfigEntity next = buildEntity(request);
    tariffRepository.save(next);
    return entityMapper.toTariffRates(next);
}
```

Repository addition:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT t FROM TariffConfigEntity t WHERE t.effectiveTo IS NULL")
Optional<TariffConfigEntity> findActiveTariffForUpdate();
```

This translates to `SELECT ... FOR UPDATE` at the database level. The lock is held for the
duration of the `@Transactional` method and released on commit. Concurrent writes are
serialized transparently — the second admin request waits, then proceeds against the
already-updated row.

**Why not optimistic locking?**

Optimistic locking (`@Version`) would detect the conflict after the fact and throw
`ObjectOptimisticLockingFailureException`. The caller (admin UI) would have to handle a
retry loop with user-visible "conflict" errors. Because admin tariff writes are infrequent
and the critical section is short (two inserts), pessimistic locking is simpler, has no
retry complexity, and carries no meaningful throughput cost.

**Why not a unique index alone?**

The existing partial unique index catches the constraint violation at the database level,
but that produces a `DataIntegrityViolationException` rather than a controlled API error,
and only after all the writes have been attempted. The lock prevents the race entirely.

---

### 4. Impact on `DeliveryCalculationService`

The read path in `DeliveryCalculationService.calculate()` does **not** need a lock.
It runs under `@Transactional(readOnly = true)` semantics and always sees a consistent
snapshot. A tariff switch that commits between two parallel calculations means one
calculation uses the old tariff and the next uses the new one — both are correct because
each calculation records the `tariff_id` it used.

The only change needed here is to verify that the existing `@Transactional` annotation
does not accidentally conflict with the write-side lock. Using the default
`PROPAGATION_REQUIRED` with `READ COMMITTED` isolation on both sides is correct:
read and write transactions are independent.

---

### 5. Cache Invalidation (if Redis is added)

If a `@Cacheable` cache is placed on `findActiveTariff()` for multi-replica deployments,
the admin `createTariff()` method must evict it:

```java
@CacheEvict(value = "activeTariff", allEntries = true)
@Transactional
public TariffRates createTariff(CreateTariffRequest request) { ... }
```

The eviction must happen **after commit**. Use `@TransactionalEventListener(phase = AFTER_COMMIT)`
if strict ordering is required, so replicas do not serve stale data from a transaction that
could still roll back.

---

### 6. Schema — No Changes Needed

The existing schema already supports the admin feature:

- `tariff_config` has `effective_from` / `effective_to` for history
- `cargo_surcharge` has its own temporal columns for future per-surcharge versioning
- The partial unique index already enforces one active tariff
- `delivery_calculation.tariff_id` already records which tariff was used

The only migration needed would be to add an `api_key_hash` table if API keys are stored
in the DB rather than as a single environment variable.

---

### 7. Admin React SPA

The SPA consumes the admin REST endpoints. Key points:

- Must send `X-API-Key` header on every admin request
- The `POST /api/admin/tariffs` response returns the newly created active tariff so the
  UI can immediately render the updated state without a second round-trip
- No need for polling or websockets — tariff changes are infrequent and admin-initiated
- CORS must be configured in `SecurityConfiguration` (or `WebMvcConfigurer`) when the
  SPA origin differs from the API origin

---

## Summary of New Classes

| Class                        | Package          | Purpose                                      |
|------------------------------|------------------|----------------------------------------------|
| `AdminTariffController`      | `controller`     | Admin CRUD endpoints                         |
| `CreateTariffRequest`        | `controller/dto` | Input DTO for new tariff                     |
| `TariffResponse`             | `controller/dto` | Output DTO for tariff reads                  |
| `AdminTariffService`         | `service`        | Pessimistic-lock write + history read        |
| `ApiKeyAuthFilter`           | `filter`         | Guards `/api/admin/**`                       |
| `SecurityConfiguration`      | `config`         | CORS + filter chain order                    |
| `AdminTariffServiceUnitTest` | `test`           | Lock contention and concurrent write scenarios |

Zero changes to `DeliveryCalculationService`, `DeliveryController`, or any existing entity.
