> **STATUS: SUPERSEDED — exploration document**
>
> This document explored 5 candidate architectures during the design phase. The team chose
> **Layered MVC** (with strict Model/Entity separation). See [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md)
> for the current architecture and [`docs/adr/0001-layered-mvc.md`](../adr/0001-layered-mvc.md)
> for the rationale.
>
> Kept here for historical context.

---

# Architecture Solutions — Delivery Price Calculation API

High-level comparison of candidate architectures within the fixed tech stack:
**Spring Boot 3 · Spring Data JPA · PostgreSQL · Docker · OpenAPI**

---

## Candidates

1. [Traditional Layered (3-Tier MVC)](#1-traditional-layered-3-tier-mvc)
2. [Hexagonal (Ports & Adapters)](#2-hexagonal-ports--adapters)
3. [Clean Architecture (Uncle Bob)](#3-clean-architecture-uncle-bob)
4. [CQRS + Layered](#4-cqrs--layered)
5. [Modular Monolith + Clean Architecture](#5-modular-monolith--clean-architecture)

---

## Quick Comparison

| Criterion           | Layered |  Hexagonal   | Clean Arch |   CQRS   | Modular Monolith |
|---------------------|:-------:|:------------:|:----------:|:--------:|:----------------:|
| Complexity          |   Low   |    Medium    |    High    |   High   |      Medium      |
| Testability         | Medium  |     High     | Very High  |   High   |       High       |
| SOLID adherence     | Medium  |     High     | Very High  |   High   |    Very High     |
| Framework coupling  |  High   |     Low      |  Very Low  |   Low    |       Low        |
| Boilerplate (files) |   Low   |    Medium    |    High    |   High   |      Medium      |
| Learning curve      |   Low   |    Medium    |    High    |   High   |      Medium      |
| YAGNI compliance    |  High   |    Medium    |    Low     |   Low    |      Medium      |
| TDD friendliness    | Medium  |     High     | Very High  |   High   |       High       |
| Fit for this app    |  Good   | **Best fit** |    Good    | Overkill |     Overkill     |

---

## 1. Traditional Layered (3-Tier MVC)

### Package Structure

```
com.coube.delivery
├── controller/          @RestController — HTTP binding, DTO validation
├── service/             @Service — business logic
├── repository/          @Repository extends JpaRepository
├── entity/              @Entity — JPA-mapped DB tables
├── dto/
│   ├── request/         DeliveryCalculateRequest (+ @Valid annotations)
│   └── response/        DeliveryCalculateResponse
└── exception/           @ControllerAdvice — global error handling → 400/500
```

### How it works

Controller receives request → delegates to Service → Service queries Repository if needed → maps to response DTO.
Framework annotations (`@Service`, `@RestController`, `@Repository`) are used throughout all layers.

### Pros

- Every Spring developer recognizes it immediately — zero onboarding cost
- Spring Boot autoconfiguration plugs in naturally at every layer
- Minimal boilerplate for a small surface area
- `PriceCalculationService` can still be a plain `@Service` unit-tested without loading Spring context

### Cons

- Domain logic lives in `@Service` classes that import `org.springframework.*` — framework bleeds into business rules
- Adding a second delivery channel (gRPC, Kafka consumer) means duplicating validation and mapping logic
- Temptation to put logic in Controllers or Entities over time ("fat controller / fat entity" anti-pattern)
- Hard to enforce the dependency rule — nothing prevents a Repository from being injected into a Controller

### SOLID Assessment

| Principle | Risk                                                                       |
|-----------|----------------------------------------------------------------------------|
| SRP       | Medium — Services tend to accumulate responsibilities                      |
| OCP       | Low — adding cargo types requires modifying the switch/when in the service |
| DIP       | Low — Services depend on concrete JPA repositories, not abstractions       |

### TDD Fit

Medium. You can unit-test the Service in isolation, but because Services often depend on JPA entities (Spring-managed
objects), tests frequently need `@ExtendWith(SpringExtension)` or careful mocking.

### When to choose

Proof of concept, internal tools, or teams that must onboard quickly and the API surface is known to stay small.

---

## 2. Hexagonal (Ports & Adapters)

### Package Structure

```
com.coube.delivery
├── domain/
│   ├── model/
│   │   ├── DeliveryCalculation.kt   value object (immutable, no framework imports)
│   │   ├── CargoType.kt             enum
│   │   └── PriceBreakdown.kt        value object
│   └── service/
│       └── PriceCalculator.kt       pure domain logic, no dependencies
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   └── CalculateDeliveryUseCase.kt   inbound port (interface)
│   │   └── out/
│   │       └── DeliveryAuditPort.kt          outbound port (e.g., save to DB)
│   └── service/
│       └── DeliveryApplicationService.kt     implements CalculateDeliveryUseCase
├── adapter/
│   ├── in/
│   │   └── web/
│   │       ├── DeliveryController.kt         @RestController
│   │       ├── DeliveryRequestDto.kt
│   │       └── DeliveryResponseDto.kt
│   └── out/
│       └── persistence/
│           ├── DeliveryAuditJpaAdapter.kt    implements DeliveryAuditPort
│           ├── DeliveryAuditJpaEntity.kt     @Entity
│           └── DeliveryAuditJpaRepository.kt extends JpaRepository
└── config/
    └── BeanConfiguration.kt                  @Configuration wiring
```

### How it works

The HTTP adapter maps the incoming DTO to a domain command and calls the inbound port. The application service (use
case) orchestrates domain logic via `PriceCalculator` and calls outbound ports (persistence, notifications, etc.). The
domain has zero framework imports.

### Pros

- Domain and application layers are framework-agnostic — testable with plain `new` operators, no Spring context needed
- Adding a second adapter (gRPC, Kafka) is zero-impact on domain logic
- Outbound ports (interfaces) make mocking trivial and meaningful in tests
- Dependency Inversion is structurally enforced: nothing in `domain/` or `application/` can import from `adapter/`
- Aligns perfectly with TDD: write the use case test first, implement the port, then the adapter

### Cons

- More files and indirection than Layered for a single endpoint
- `CalculateDeliveryUseCase` as an interface with a single implementation is debatable under YAGNI — but justified by
  testability
- Mapping between DTOs ↔ domain objects ↔ JPA entities requires careful attention (3 separate model hierarchies)
- Developers unfamiliar with the pattern need time to understand the port/adapter naming convention

### SOLID Assessment

| Principle | Risk                                                                          |
|-----------|-------------------------------------------------------------------------------|
| SRP       | Very Low — each class has exactly one job                                     |
| OCP       | Low — adding OVERSIZED cargo type only changes the domain enum and calculator |
| DIP       | Very Low — application depends on port interfaces, not concrete adapters      |

### TDD Fit

High. `PriceCalculator` is a pure function. `DeliveryApplicationService` depends only on interfaces — tests inject
fakes. Controller tests use `@WebMvcTest` with a mocked use case.

### When to choose

This is the **recommended architecture** for the current requirements. Balances testability, SOLID compliance, and
realistic complexity for a production Spring Boot service that will grow beyond one endpoint.

---

## 3. Clean Architecture (Uncle Bob)

### Package Structure

```
com.coube.delivery
├── entity/                           Enterprise business rules
│   ├── DeliveryCalculation.java      core entity, no framework
│   ├── CargoType.java
│   └── PriceBreakdown.java
├── usecase/                          Application business rules
│   ├── boundary/
│   │   ├── in/
│   │   │   ├── CalculateDeliveryInputBoundary.java
│   │   │   └── CalculateDeliveryInputData.java
│   │   └── out/
│   │       ├── CalculateDeliveryOutputBoundary.java
│   │       └── CalculateDeliveryOutputData.java
│   └── interactor/
│       └── CalculateDeliveryInteractor.java
├── interfaceadapter/
│   ├── controller/
│   │   └── DeliveryController.java   @RestController
│   ├── presenter/
│   │   └── DeliveryPresenter.java    implements OutputBoundary
│   └── gateway/
│       └── DeliveryAuditGateway.java implements outbound port
└── framework/
    ├── web/                           Spring MVC, OpenAPI config
    └── db/                            JPA config, @Entity classes
```

### How it works

Strict concentric-ring dependency rule: `entity` → `usecase` → `interfaceadapter` → `framework`. Each ring can only
import inward. The Controller (interface adapter) calls the Interactor (use case) through an Input Boundary interface.
The Interactor pushes output through an Output Boundary to a Presenter, which formats the response. Frameworks (Spring,
JPA) live entirely in the outermost ring.

### Pros

- The most framework-agnostic architecture of all candidates — Spring could be swapped for Micronaut with zero domain
  changes
- Dependency rule is explicit and mechanically verifiable (ArchUnit tests)
- Maximum testability — every inner ring is testable with no infrastructure
- Expressive boundary interfaces make the use case intent explicit

### Cons

- Highest boilerplate of all candidates — `InputData`, `OutputData`, `InputBoundary`, `OutputBoundary`, `Interactor`,
  `Presenter` for every use case
- The Presenter pattern is awkward in REST APIs (HTTP response is synchronous, Presenter adds an indirection layer that
  feels unnatural)
- Violates YAGNI for a single endpoint — the ceremony exceeds the problem
- Steepest learning curve; requires team consensus and strict review enforcement
- Risk of cargo-culting the structure without understanding the motivation

### SOLID Assessment

| Principle | Risk                                                                              |
|-----------|-----------------------------------------------------------------------------------|
| SRP       | Very Low                                                                          |
| OCP       | Very Low                                                                          |
| DIP       | Very Low — structurally impossible to violate if packages are organized correctly |

### TDD Fit

Very High. Every boundary is an interface. Every ring is independently testable. However, the boilerplate means more
test setup code per use case.

### When to choose

Teams with deep Clean Architecture experience. Services expected to run on multiple frameworks or runtimes. When the
input/output model is complex enough that Presenter separation pays off (e.g., multiple response formats: JSON, XML,
CSV). **Not recommended as the starting point for this service** — it's the right destination if the system grows
significantly.

---

## 4. CQRS + Layered

### Package Structure

```
com.coube.delivery
├── command/
│   ├── CalculateDeliveryCommand.java     immutable command object
│   ├── CalculateDeliveryHandler.java     handles the command, returns result
│   └── CommandBus.java                   dispatches commands to handlers
├── query/
│   ├── GetDeliveryHistoryQuery.java
│   ├── GetDeliveryHistoryHandler.java
│   └── DeliveryHistoryReadModel.java     optimized read DTO
├── domain/
│   ├── DeliveryCalculation.java
│   ├── CargoType.java
│   └── PriceBreakdown.java
├── infrastructure/
│   ├── web/
│   │   └── DeliveryController.java
│   └── persistence/
│       └── DeliveryRepository.java
└── config/
    └── CqrsConfiguration.java
```

### How it works

Writes flow through a Command → CommandBus → CommandHandler pipeline. Reads use a separate Query → QueryHandler pipeline
optimized for the read model (potentially different DB projections or caches). The write and read models can diverge
independently.

### Pros

- Write and read concerns are explicitly separated — each can evolve, scale, and be optimized independently
- Natural fit for event sourcing if added later
- Command handlers are small, focused, and highly testable
- Read models can be denormalized for performance without affecting write logic

### Cons

- **Significant over-engineering for a single stateless calculation endpoint** — there is nothing to persist on the
  write side, no separate read model needed
- CommandBus adds an indirection layer with no current benefit
- Two parallel class hierarchies (Command + Query) double the boilerplate
- CQRS pays off when read/write ratios diverge dramatically or when event sourcing is introduced — neither applies here
  today
- Violates YAGNI most severely of all candidates

### SOLID Assessment

| Principle | Risk                                                 |
|-----------|------------------------------------------------------|
| SRP       | Very Low — handlers are single-purpose by definition |
| OCP       | Very Low                                             |
| DIP       | Low                                                  |

### TDD Fit

High. Handlers are plain classes with injected dependencies.

### When to choose

When read and write traffic ratios differ by orders of magnitude. When event sourcing or audit logs require a separate
event store. When the system manages complex aggregates with many concurrent writers. **None of these apply to this
service in its current scope.**

---

## 5. Modular Monolith + Clean Architecture

### Package Structure

```
com.coube
├── shared/
│   ├── domain/               shared value objects, Money, Currency
│   └── infrastructure/       common Spring config, error handling
└── module/
    └── delivery/
        ├── DeliveryModuleConfig.java    @Configuration, module boundary
        ├── api/
        │   └── DeliveryFacade.java      public API of this module (interface)
        ├── domain/
        │   ├── model/
        │   │   ├── DeliveryCalculation.java
        │   │   ├── CargoType.java
        │   │   └── PriceBreakdown.java
        │   └── service/
        │       └── PriceCalculator.java
        ├── application/
        │   └── service/
        │       └── DeliveryApplicationService.java  implements DeliveryFacade
        └── infrastructure/
            ├── web/
            │   ├── DeliveryController.java
            │   └── DeliveryDto.java
            └── persistence/
                ├── DeliveryJpaRepository.java
                └── DeliveryJpaEntity.java
```

### How it works

The application is a single deployable JAR but organized into self-contained modules. Each module exposes a `Facade`
interface as its public API. Other modules interact only through the Facade, never by reaching into internal packages.
Inside each module, Clean Architecture or Hexagonal conventions apply. Splitting a module into a microservice later
requires extracting the module with minimal changes.

### Pros

- Module boundaries enforce encapsulation at compile time (enforced via ArchUnit or Java modules)
- Each module is independently testable and has a clear ownership boundary
- Extraction to microservices is mechanical — the module boundary is already the service boundary
- Scales well as the system grows to 5–15 bounded contexts
- Shared infrastructure (auth, observability, error handling) lives in `shared/` without duplication

### Cons

- Overkill for a single bounded context (delivery calculation) with one endpoint
- Adds a `Facade` indirection layer that has no current consumer other than the HTTP controller
- Module governance requires team discipline (code reviews must enforce module boundaries)
- `shared/` package tends to become a dumping ground if not actively curated

### SOLID Assessment

| Principle | Risk                                                     |
|-----------|----------------------------------------------------------|
| SRP       | Very Low — modules are single-bounded-context            |
| OCP       | Very Low                                                 |
| DIP       | Very Low — modules communicate through Facade interfaces |

### TDD Fit

High. Module API tests exercise the Facade. Internal tests are isolated per module.

### When to choose

When the system is expected to grow to multiple bounded contexts (pricing, routing, shipment tracking, customer
management) and the team wants to defer the microservices decision until boundaries are clear. **Right architecture if
this is the first of many modules; premature if it stays a single delivery calculator.**

---

## Recommendation

Given the stated requirements (Spring Boot, scaleable, robust, TDD, clean architecture, single endpoint to start):

### Primary Recommendation: **Hexagonal (Ports & Adapters)**

It hits the best balance of:

- Clean Architecture compliance without Presenter ceremony
- Full SOLID adherence (especially DIP via ports)
- TDD friendliness — domain and application layers need no mocks
- Realistic growth path — adding endpoints, a DB audit table, or a second adapter requires zero rework of existing code
- Manageable file count for the current scope

### If growth to multiple domains is planned: **Modular Monolith + Hexagonal per module**

Start with the `delivery` module using Hexagonal internally. Add `routing`, `customer`, etc. as separate modules over
time without restructuring.

### Avoid for this service:

- **CQRS** — no read/write split needed; violates YAGNI
- **Modular Monolith alone** — justified only if a second module is already planned
- **Pure Clean Architecture** — correct destination, not correct starting point; introduce it incrementally if
  complexity warrants
