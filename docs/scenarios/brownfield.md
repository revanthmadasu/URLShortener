# Scenario 2 — Brownfield: refactor code generation, add caching, close a security gap

This scenario demonstrates **codebase reasoning**: changing an existing, tested system
without breaking it. It is delivered as three independently reviewable changes.

## Impact analysis (done before touching code)

The starting point is the Phase 1 core. Before changing anything, we map what each change
touches:

| Change | Modules touched | Data flow | Risk to existing behavior |
|---|---|---|---|
| **2A** Replace random code-gen with a collision-free strategy behind an interface | `RandomShortCodeGenerator` (→ interface), `LinkService` (injection point), new migration (sequence) | Create path only. Redirect/fetch/delete unaffected. | Medium — code-gen is on the write path; existing tests pin behavior. |
| **2B** Redis cache-aside for redirects + circuit breaker | `LinkService.resolveForRedirect`, new `RedirectCache`, config | Read path (`GET /{code}`). | Medium — must degrade gracefully when Redis is down; must not serve **expired** links from cache. |
| **2C** Block SSRF / private-network targets | `UrlValidator` | Create path validation. | Low functional, high security value. Could reject some legitimate hosts (documented). |

## Key reasoning: why "collision-free" generation still needs the DB arbiter

Generated codes and **custom aliases share one namespace** (`links.short_code`). A user can
register the alias `0000001`, which a future generated code might also produce. Therefore even
a bijective, collision-free generator (2A) cannot assume its output is free — the unique index
remains the arbiter, and `LinkService`'s insert-and-retry loop stays. What 2A removes is the
*probabilistic self-collision* of random generation as the keyspace fills, not the need for
the database check. This is why the refactor keeps the retry loop but changes its meaning.

## 2A — Sequence + Feistel permutation

**Intent.** Replace `random + retry` (whose retry rate grows as the space fills) with a
`sequence + Feistel` codec that is unique by construction and emits non-sequential-looking
codes (so the public code does not leak creation order or total volume).

**Design.**
- A dedicated Postgres sequence `link_code_seq` yields a dense, monotonic counter.
- A format-preserving **Feistel** permutation maps that counter to a pseudo-random value in
  `[0, 62^length)` using **cycle-walking** to stay inside the base62 domain. Feistel is a
  bijection, so distinct counters → distinct codes: no self-collision, no retry-for-collision.
- Selectable via `app.code.strategy = feistel | random` (Strategy pattern), so the change is
  reversible at runtime and the old behavior remains available.

**Validation.** Property tests assert the codec is injective over a large contiguous range and
always emits fixed-length base62; existing create/redirect tests must stay green (behavior
preserved for callers).

## 2B — Cache-aside with graceful degradation *(see code + ADR-0003)*

## 2C — SSRF / private-network blocking *(see code + risk R3)*
