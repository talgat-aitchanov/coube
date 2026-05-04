# Architecture — Delivery Price Calculation API

## Overview

A Spring Boot REST service exposing a single endpoint that computes delivery prices using
configurable tariffs stored in PostgreSQL. Each calculation is persisted as an audit record.
Admin/tariff management is **deferred** — see [README.md](../README.md) for the deferred-features list.

**Stack:** Java 17 · Spring Boot 3.4 · Gradle (Groovy DSL) · MapStruct · Lombok · Flyway · OpenAPI · Docker

---

## Architecture Style: Layered MVC with Strict Model/Entity Separation

```
HTTP Request
     │
┌────▼────────────────┐
│   Controller Layer  │  DeliveryController, DTOs (records), DeliveryDtoMapper
└────┬────────────────┘
     │ Domain model
┌────▼────────────────┐
│   Service Layer     │  DeliveryCalculationService (@Transactional, formula + persistence)
└────┬────────────────┘
     │ Domain model ↔ Entity (via DeliveryEntityMapper)
┌────▼────────────────┐
│  Persistence Layer  │  Repositories, JPA Entities (Lombok)
└────┬────────────────┘
     │
  PostgreSQL
```

**Key principles:**

- JPA `@Entity` classes never leave the persistence layer.
- Services and controllers operate exclusively on framework-free domain models (records).
- Mappers (MapStruct) translate at every layer boundary.
- This is **Layered MVC with clean separation**, not strict Clean Architecture (services have `@Service`).

For the architectural decision rationale, see [`adr/0001-layered-mvc.md`](adr/0001-layered-mvc.md).

---

## Package Structure

```
com.coube.delivery
├── DeliveryApplication.java             @SpringBootApplication
│
├── controller/
│   ├── DeliveryController.java          @RestController — POST /api/v1/delivery/calculate
│   └── dto/
│       ├── CalculateRequest.java        record + @Valid
│       ├── CalculateResponse.java       record
│       └── DeliveryConstraints.java     @UtilityClass — validation boundary constants
│
├── service/
│   └── DeliveryCalculationService.java  @Service @Transactional — orchestrates flow
│
├── model/                               Pure Java records — zero framework annotations
│   ├── CargoType.java                   enum: FRAGILE, OVERSIZED, STANDARD
│   ├── Currency.java                    enum: KZT
│   ├── DeliveryInput.java               record
│   ├── PriceBreakdown.java              record
│   └── TariffRates.java                 record (id, baseRate, urgentRate, Map<CargoType, BigDecimal>)
│
├── entity/                              JPA @Entity (Lombok) — only used in persistence layer
│   ├── TariffConfigEntity.java
│   ├── CargoSurchargeEntity.java        includes effectiveFrom/effectiveTo/createdAt audit columns
│   └── DeliveryCalculationEntity.java
│
├── repository/
│   ├── TariffConfigRepository.java      JpaRepository + findActiveTariff()
│   └── DeliveryCalculationRepository.java
│
├── mapper/
│   ├── DeliveryDtoMapper.java           @Mapper (MapStruct): DTO ↔ domain model
│   └── DeliveryEntityMapper.java        @Mapper (MapStruct): domain model ↔ entity
│
├── filter/
│   ├── CorrelationIdFilter.java         Propagates X-Correlation-Id header; generates one if absent
│   └── RequestLoggingFilter.java        Logs method, URI, status, and duration for every request
│
├── config/
│   ├── MetricsConfiguration.java        Registers TimedAspect bean for @Timed AOP interception
│   └── OpenApiConfiguration.java        OpenAPI info + server URL configuration
│
└── exception/
    ├── GlobalExceptionHandler.java      @RestControllerAdvice — returns RFC 7807 ProblemDetail
    └── TariffNotFoundException.java     RuntimeException
```

**Total: ~25 source classes + 1 Flyway migration + main app class**

---

## Data Flow

```
POST /api/v1/delivery/calculate (CalculateRequest DTO)
  → DeliveryDtoMapper.toInput(request)              → DeliveryInput (domain)
  → DeliveryCalculationService.calculate(input):
      1. TariffConfigRepository.findActiveTariff()   → TariffConfigEntity
      2. DeliveryEntityMapper.toTariffRates(entity)  → TariffRates (domain)
      3. compute basePrice / surcharges / total      → PriceBreakdown (domain)
      4. DeliveryEntityMapper.toEntity(...)          → DeliveryCalculationEntity
      5. DeliveryCalculationRepository.save(entity)  (audit row)
      6. return PriceBreakdown
  → DeliveryDtoMapper.toResponse(breakdown)          → CalculateResponse DTO
  → 200 OK
```

The service method is annotated `@Transactional` — read tariff + write audit happen in one transaction.

---

## Business Formula

```
basePrice          = distanceKm × weightTon × baseRate
urgentSurcharge    = isUrgent ? basePrice × urgentRate : 0
cargoTypeSurcharge = basePrice × cargoRates[cargoType]
totalPrice         = basePrice + urgentSurcharge + cargoTypeSurcharge
currency           = KZT (hardcoded in service; tariff rates are currency-agnostic multipliers)
```

All arithmetic uses `BigDecimal` with explicit scale (2 decimal places, `HALF_UP` rounding).

---

## Database Schema

Everything lives in a single migration: `V1__create_schema.sql` (schema + seed data).

```sql
CREATE TABLE tariff_config
(
    id             BIGSERIAL PRIMARY KEY,
    base_rate      DECIMAL(12, 4) NOT NULL,
    urgent_rate    DECIMAL(6, 4)  NOT NULL,
    effective_from TIMESTAMPTZ    NOT NULL,
    effective_to   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Invariant: at most one active tariff at a time. DB enforces — no DISTINCT needed in queries.
CREATE UNIQUE INDEX one_active_tariff
    ON tariff_config ((1)) WHERE effective_to IS NULL;

CREATE TABLE cargo_surcharge
(
    id             BIGSERIAL PRIMARY KEY,
    tariff_id      BIGINT        NOT NULL REFERENCES tariff_config (id) ON DELETE CASCADE,
    cargo_type     VARCHAR(20)   NOT NULL CHECK (cargo_type IN ('FRAGILE', 'OVERSIZED', 'STANDARD')),
    surcharge_rate DECIMAL(6, 4) NOT NULL,
    effective_from TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    effective_to   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (tariff_id, cargo_type)
);

CREATE TABLE delivery_calculation
(
    id                   BIGSERIAL PRIMARY KEY,
    tariff_id            BIGINT REFERENCES tariff_config (id),
    distance_km          DECIMAL(8, 2)  NOT NULL,
    weight_ton           DECIMAL(8, 2)  NOT NULL,
    cargo_type           VARCHAR(20)    NOT NULL,
    is_urgent            BOOLEAN        NOT NULL,
    base_price           DECIMAL(14, 2) NOT NULL,
    urgent_surcharge     DECIMAL(14, 2) NOT NULL,
    cargo_type_surcharge DECIMAL(14, 2) NOT NULL,
    total_price          DECIMAL(14, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL DEFAULT 'KZT',
    calculated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delivery_calculation_calculated_at
    ON delivery_calculation (calculated_at DESC);
```

`currency` is only stored in `delivery_calculation` (the output). `tariff_config` rates are
currency-agnostic multipliers — a future FX service can apply its own conversion before storing.

DB-generated columns (`created_at`, `calculated_at`, cargo surcharge temporal fields) are declared
`insertable = false, updatable = false` in JPA and annotated `@Generated(GenerationTime.INSERT)`.

Seeded via `WITH … INSERT` inside the same V1 file: base rate 8 KZT/km/ton, urgent rate 20%,
three cargo surcharges (STANDARD 0%, FRAGILE 10%, OVERSIZED 25%).

---

## Validation → 400

`CalculateRequest`:

- `distanceKm`: `@NotNull @DecimalMin("1") @DecimalMax("5000")`
- `weightTon`: `@NotNull @DecimalMin("0.1") @DecimalMax("120")`
- `cargoType`: `@NotNull` (enum — invalid value handled by Jackson + `HttpMessageNotReadableException`)
- `isUrgent`: `@NotNull`

Boundary constants live in `DeliveryConstraints` (`@UtilityClass`), referenced from the request record.

`GlobalExceptionHandler` returns **RFC 7807 ProblemDetail**:

- `MethodArgumentNotValidException` → 400 with field errors
- `HttpMessageNotReadableException` → 400 (malformed JSON / bad enum value)
- `TariffNotFoundException` → 503 (no active tariff configured)
- Unhandled → 500

---

## Operational Concerns

- **Correlation IDs** — `CorrelationIdFilter` reads `X-Correlation-Id` from the request (or generates a UUID), puts it in MDC, and echoes it back in the response header. `RequestLoggingFilter` logs method, URI, status, and duration using the MDC key.
- **Metrics** — `@Timed(value = "delivery.calculations.total")` on the controller method. `MetricsConfiguration` registers a `TimedAspect` bean so Spring AOP intercepts the annotation and records timing, count, and error tags automatically. Exposed via `/actuator/metrics` (Micrometer + Prometheus).
- **`@Transactional`** on service methods that read + write.
- **HikariCP** pool size tunable via `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`.
- **`spring.jackson.time-zone=UTC`** — prevents TZ surprises with `TIMESTAMPTZ`.
- **Structured JSON logging** in `prod` profile (Logback console encoder).

No tariff caching — a single indexed Postgres lookup is ~1 ms. Caching is added when the admin
feature arrives (see deferred features in README).

---

## Infrastructure

### Local development

```bash
docker compose up -d           # starts Postgres
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Containerized deployment

- Multi-stage `Dockerfile` builds the JAR, runs on `eclipse-temurin:17-jre-alpine`.
- Image is non-root, exposes port 8080.

### Profiles

- `local` — Docker Compose Postgres, verbose SQL logs, Swagger UI enabled
- `test` — Testcontainers Postgres, no Docker Compose dependency
- `prod` — env vars only (`SPRING_DATASOURCE_*`), Swagger UI disabled

---

## Testing Strategy

### Unit (no Spring context)

| Test class                             | What                                                  | Approach                       |
|----------------------------------------|-------------------------------------------------------|--------------------------------|
| `DeliveryCalculationServiceUnitTest`   | Formula × 3 cargo types × urgent/non-urgent + boundaries | Plain JUnit, mock repositories |
| `DeliveryDtoMapperUnitTest`            | DTO ↔ domain conversions                              | MapStruct generated impl       |
| `DeliveryEntityMapperUnitTest`         | Domain ↔ entity conversions                           | MapStruct generated impl       |
| `LayeringRulesUnitTest`               | Layer boundary enforcement                            | ArchUnit                       |

### Integration (single slice, Testcontainers)

| Test class                              | Slice                         | What                                     |
|-----------------------------------------|-------------------------------|------------------------------------------|
| `DeliveryControllerIntegrationTest`     | `@WebMvcTest`                 | Happy path 200 + validation 400 + 503    |
| `TariffConfigRepositoryIntegrationTest` | `@DataJpaTest` + Postgres TC  | `findActiveTariff()` returns seeded tariff |

### E2E (full Spring context, Testcontainers)

| Test class                 | Covers                                                                         |
|----------------------------|--------------------------------------------------------------------------------|
| `CalculateDeliveryE2ETest` | FRAGILE/STANDARD/OVERSIZED breakdowns, validation 400, DB persistence, correlation ID header, actuator health + metrics |

### Coverage

JaCoCo merges execution data from all three test suites. Reports at `build/reports/jacoco/jacocoCoverageReport/`:
- `html/index.html` — browsable HTML
- `jacocoCoverageReport.xml` — XML for CI / SonarQube

### Quality gates (ArchUnit)

- `model/` package has no `org.springframework.*` or `jakarta.persistence.*` imports
- `entity/` package is accessed only from `repository/`, `mapper/`, and `service/` packages
- `controller/` package does not depend on `entity/` or `repository/` packages

---

## Path to Admin Feature (deferred)

When admin CRUD arrives, **zero changes to existing classes**:

1. Add `POST /api/admin/tariffs` controller — closes current row, inserts new row
2. Add `ApiKeyAuthFilter` for `/api/admin/**`
3. Add Redis + `@Cacheable` if multi-replica cache invalidation matters
4. Schema is already correct (insert-only history pattern, partial unique index)
