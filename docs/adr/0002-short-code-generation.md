# ADR-0002: Short-code generation strategy

- **Status:** Accepted
- **Date:** 2026-08-05
- **Supersedes:** the Phase 1 random-only approach

## Context

Phase 1 generated codes randomly and relied on the DB unique index + retry to handle
collisions. This works but has two drawbacks: (1) as the keyspace fills, the collision (retry)
rate rises; (2) random codes reveal nothing, which is fine, but we also wanted a deterministic,
capacity-aware scheme without giving up unpredictability of creation order.

## Decision

Introduce a `ShortCodeGenerator` **strategy interface** with two implementations, selected by
`app.code.strategy`:

- **`feistel` (default):** `base62( feistel( nextval(link_code_seq) ) )`. A dense Postgres
  sequence feeds a **Feistel permutation** (a bijection) reduced to the base62 domain via
  **cycle-walking**. Distinct counters → distinct codes, so there is **no self-collision** and
  no probabilistic retry. Output is non-sequential, so codes don't leak creation order/volume.
- **`random`:** the Phase 1 behavior, retained for comparison and as a fallback.

## Consequences

- **(+)** Uniqueness is guaranteed by construction; the retry loop is now effectively never
  taken for self-collisions.
- **(+)** Capacity is explicit: at `62^7 ≈ 3.5×10¹²` codes, exhaustion raises a clean error.
- **(+)** The refactor is reversible at runtime (config flag) — safe change management.
- **(−)** Introduces a sequence (shared write hotspot). Acceptable: `nextval` is cheap and
  non-transactional; if it ever became a bottleneck we'd batch ranges per instance.
- **Important:** the DB unique index **stays the arbiter**. Generated codes and custom aliases
  share one namespace, so a generated code can still collide with a pre-existing alias; the
  service retries (which draws the next counter). The Feistel change removes *self*-collision,
  not the need for the DB check.

## Alternatives considered

- **Hashids / random-until-unique:** simple but same probabilistic retry as `random`.
- **Snowflake-style IDs base62-encoded:** longer codes; leaks time ordering.
- **Encrypt the id with AES-FF1 (true FPE):** stronger unpredictability, heavier dependency;
  overkill since codes are not secrets.
