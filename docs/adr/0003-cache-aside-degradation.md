# ADR-0003: Redirect caching & graceful degradation

- **Status:** Accepted
- **Date:** 2026-08-05

## Context

`GET /{code}` is the hot path and dominates traffic. Hitting Postgres for every redirect is
wasteful and couples redirect latency/availability to the database. We want a cache — but a
cache must never turn a cache problem into a user-facing outage, and must never serve stale
(expired/deleted) links.

## Decision

Cache-aside in Redis (`url:{code}` → destination), fronting `LinkService.resolveTargetUrl`:

1. **Lookup** Redis. On **hit**, return. On **negative-hit** (sentinel), return 404. On
   **miss**, load from Postgres, populate the cache, return.
2. **Graceful degradation:** every Redis call is wrapped; any failure is treated as a miss and
   the request proceeds against Postgres. A small **circuit breaker** stops calling Redis after
   repeated failures (so we don't pay its timeout per request) and retries after a cooldown.
3. **Redis is excluded from health/liveness** — a cache outage degrades latency, not
   availability.

## Correctness guardrails (the subtle parts)

- **Expiry vs. cache:** a cache hit bypasses the DB and therefore the expiry check. So the
  positive TTL is **bounded by the link's remaining lifetime** (`min(cacheTtl, untilExpiry)`).
  An expired link can never be served from cache because its entry expires no later than it does.
- **Delete vs. cache:** `LinkService.delete` **evicts** the cache entry so a deleted link stops
  redirecting immediately rather than lingering until TTL.
- **Negative cache:** unknown codes are remembered briefly (short TTL) to shield the DB from
  scanning/probing, without hiding newly created links for long.

## Consequences

- **(+)** Redirects are served from memory on the common path; DB load drops sharply.
- **(+)** Redis can fail or be absent entirely and the service still works (slower).
- **(−)** Small write-path cost (evict on delete) and reasoning overhead (TTL bounding).
- **Validation:** unit tests cover degradation (Redis throwing → miss), breaker open/half-open,
  TTL bounding, and no-positive-cache-for-expired; an integration test proves a cached redirect
  survives out-of-band deletion of the DB row (i.e. it was truly served from cache).

## Alternatives considered

- **Resilience4j** for the breaker — rejected to avoid an unvetted dependency on Spring Boot 4;
  the needed behavior is small and is implemented + unit-tested directly with an injected clock.
- **Read-through via Spring Cache abstraction** — hides the degradation/eviction/TTL-bounding
  logic we specifically need to control here.
