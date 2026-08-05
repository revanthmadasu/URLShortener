# ADR-0004: Analytics — what a "click" is, and how it's captured

- **Status:** Accepted
- **Date:** 2026-08-05
- **Context:** The requirement "add analytics" was ambiguous; see
  [ambiguous scenario](../scenarios/ambiguous.md) for the full ambiguity register.

## Decision

- **A click = one successful redirect (302).** 404/410 are not clicks.
- **Capture is asynchronous and best-effort** (`clickExecutor`, virtual threads). It never
  blocks or fails a redirect (decision A8): a persistence failure is logged and dropped.
- **Events are keyed by `short_code`, not `links.id`.** Redirects can be served entirely from
  the Redis cache without loading the link row, so keying by code lets capture work on the
  cache-hit path.
- **Privacy first:** the client IP is stored only as `HMAC-SHA256(ip, app.analytics.ip-salt)`;
  the raw IP is never persisted. "Unique visitors" is therefore an estimate over salted hashes.
- **UTC day buckets** for time series; **top-5 referrers**; window selected via `?days=N`.
- **Retention:** a daily scheduled sweep deletes events older than `app.analytics.retention`.

## Consequences

- **(+)** Redirect latency is unaffected by analytics; analytics can fail independently.
- **(+)** Minimal PII footprint; retention bounds it further.
- **(−)** Aggregation is on-read SQL — fine at prototype scale; the scaling path is periodic
  rollups / a counter store (noted as a limitation).
- **(−)** Async capture means stats are eventually consistent (sub-second normally); tests wait
  for capture via Awaitility.
- **(−)** No bot filtering: counts include crawlers/prefetchers (documented limitation).

## Alternatives considered

- **Synchronous counting on the redirect path** — simplest, but couples redirect latency and
  availability to the analytics write. Rejected (A4/A8).
- **Redis counters only** — O(1) totals but loses per-day/referrer breakdowns and durability.
  Rejected as the primary store; could augment later for hot totals.
- **Store raw IP / geo-IP / UA parsing** — richer dimensions, more PII and dependencies.
  Deferred.
