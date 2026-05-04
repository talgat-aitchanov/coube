# ADR 0002: Records for Value Objects, Lombok for Entities

## Status

Accepted — 2026-05-04

## Context

The project needs concise, immutable value objects (DTOs, domain models) and mutable JPA entities.
Two main mechanisms reduce Java boilerplate: Java records (built-in since Java 16) and Lombok
(annotation processor).

## Decision

- **Use records** for: DTOs, domain models, value objects in `model/` package, enums.
- **Use Lombok** for: JPA entities (`@Getter`/`@Setter`/`@NoArgsConstructor`/`@AllArgsConstructor`/`@Builder`),
  services (`@RequiredArgsConstructor` for constructor injection, `@Slf4j` for logging).
- Configure `lombok-mapstruct-binding` so MapStruct sees the Lombok-generated accessors.

## Rationale

**Why records for value objects:**

- No annotation processor needed — built into the language.
- Immutable by default — aligns with our "no JPA in domain models" rule.
- Auto-generated `equals` / `hashCode` / `toString` based on components.
- Zero risk of "accidentally added a setter" mutating shared state.

**Why Lombok for entities:**

- JPA *requires* mutable fields, a no-args constructor, and accessors. Records cannot satisfy this.
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` produces the constructor JPA needs while
  keeping it inaccessible to application code.
- `@Builder` keeps entity construction readable when there are 8–10 fields (e.g., `DeliveryCalculationEntity`).

**Why Lombok for services:**

- `@RequiredArgsConstructor` removes 5–10 lines of constructor injection boilerplate per service.
- `@Slf4j` is a one-line logger declaration.

## Consequences

### Positive

- Domain models stay free of any annotation processor (records are language-native).
- Persistence-layer boilerplate is minimal.
- Both tools serve their natural purpose; no overlap.

### Negative

- Two boilerplate-reduction mechanisms in one codebase — small cognitive overhead.
- Lombok requires IDE plugin support (IntelliJ has it built-in; reviewers using `gh pr diff` see
  generated code in compiled `.class` files only).
- The `lombok-mapstruct-binding` annotation processor must be on the build's `annotationProcessor`
  classpath alongside both `lombok` and `mapstruct-processor`.

## Alternatives considered

- **Lombok everywhere** — would require `@Value` immutable classes for DTOs, which works but
  duplicates a feature the language now has natively.
- **Records everywhere** — impossible for JPA entities (mutability + no-args constructor required).
