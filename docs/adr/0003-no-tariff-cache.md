# ADR 0003: No Tariff Caching in the Initial Scope

## Status

Accepted — 2026-05-04

## Context

The calculation endpoint reads the active tariff from PostgreSQL on every request. With the
admin feature deferred, tariff changes happen only via Flyway migrations (i.e., on deploy).
A natural temptation is to add Caffeine or Redis caching upfront for "scalability."

## Decision

**Do not add a tariff cache yet.** Read the active tariff per request via a Postgres lookup.

## Rationale

- The query `SELECT * FROM tariff_config WHERE effective_to IS NULL` hits a partial unique
  index (`one_active_tariff_per_currency`). Cost is sub-millisecond on a warm connection pool.
- HikariCP with default settings handles thousands of these per second per replica.
- Adding a cache now means dealing with cache invalidation later (when admin endpoints land),
  which is a known hard problem. Better to add the cache *together with* the cache-eviction
  trigger.
- Caffeine (in-process) would diverge across replicas as soon as someone updates a tariff.
  We would need Redis for correctness, which adds infrastructure complexity for no current benefit.

## Consequences

### Positive

- Fewer moving parts in `local`, `test`, and `prod` environments.
- No cache-staleness bugs while admin endpoints don't exist.
- Performance is "good enough" — a single primary-key lookup against a row that fits in shared buffers.

### Negative

- Slightly higher per-request latency vs. a hot in-process cache (≈1 ms vs ≈10 µs).
- When admin endpoints arrive, we must add caching *and* its eviction triggers in the same change set.

## Trigger to revisit

Reopen this decision when **any** of the following becomes true:

- Admin endpoints are merged → add `@Cacheable` with explicit eviction.
- Multiple replicas are deployed *and* admin endpoints exist → switch backing store to Redis.
- p99 latency for `/api/delivery/calculate` exceeds 50 ms in production.
