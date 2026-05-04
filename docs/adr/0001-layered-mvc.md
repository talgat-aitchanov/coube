# ADR 0001: Layered MVC with Strict Model/Entity Separation

## Status

Accepted — 2026-05-04

## Context

We evaluated five candidate architectures (Layered MVC, Hexagonal, Clean Architecture, CQRS,
Modular Monolith) for a Spring Boot service exposing a single delivery-price endpoint that
persists each calculation to PostgreSQL. The full comparison is preserved in
[`docs/exploration/ARCHITECTURE_SOLUTIONS.md`](../exploration/ARCHITECTURE_SOLUTIONS.md).

The team's primary constraint was **onboarding ease**: any Spring developer should be productive
on day one. A secondary constraint was **best-practice separation** so the code does not collapse
into "fat services" or expose JPA entities at the HTTP boundary.

## Decision

Use **Layered MVC** (`controller` → `service` → `repository`) with a strict invariant: domain models
(records in `model/`) are distinct from JPA entities (in `entity/`), and MapStruct mappers translate
at every layer boundary.

## Consequences

### Positive

- Every Spring developer recognizes the pattern; zero learning curve.
- Domain records have no framework dependencies — they are testable with plain `new`.
- JPA entities never leak to controllers or external callers.
- ArchUnit can mechanically enforce that `model/` has no `org.springframework.*` or
  `jakarta.persistence.*` imports.
- Adding the deferred admin feature is purely additive (new controller, new service method) — no
  refactor of existing classes is required.

### Negative

- Services carry Spring annotations (`@Service`, `@Transactional`) — strictly speaking this
  violates Clean Architecture's dependency rule. We accept this as a pragmatic trade-off and
  describe the architecture honestly as "Layered MVC with clean separation," not "Clean Architecture."
- Three parallel object hierarchies (DTO / model / entity) require MapStruct boilerplate. We
  consider this acceptable because MapStruct generates the code at compile time and the boundaries
  are clear.

## Alternatives considered

- **Hexagonal (Ports & Adapters)** — better SOLID compliance via outbound ports, but the
  port/adapter naming convention adds onboarding cost without a near-term need (no second adapter,
  no admin feature yet).
- **Clean Architecture (Uncle Bob)** — best framework isolation but the Presenter ceremony is
  awkward for synchronous REST and the file count tripled the implementation cost for a single
  endpoint.
- **CQRS** — no read/write asymmetry exists; pure overhead.
- **Modular Monolith** — only justified when multiple bounded contexts exist; we have one.
