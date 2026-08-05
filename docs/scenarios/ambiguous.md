# Scenario 3 — Ambiguous requirement: "Add analytics"

The requirement as received: **"Add analytics to the URL shortener."** That is under-specified.
This document shows the discipline applied: surface the ambiguity, decide each open question with
a rationale, normalize into an engineering spec, then build to it.

## Ambiguity register → resolutions

| # | Open question | Options | Decision & rationale |
|---|---|---|---|
| A1 | What counts as a **click**? | every request to `/{code}`; only successful redirects; only non-bot | **Only successful redirects (302).** 404/410 are not clicks. Bot filtering is out of scope (documented limitation) — over-filtering silently drops real data. |
| A2 | What is a **unique** visitor? | by IP; by IP+UA; by cookie/login | **Distinct salted IP hash.** No accounts exist (open prototype), and we refuse to store raw IPs (privacy). Approximate but privacy-preserving; documented as an estimate. |
| A3 | **PII / IP retention** | store raw IP; store hash; store nothing | **Store `HMAC-SHA256(ip, serverSalt)` only**, never the raw IP. Salt is server-side config. Minimizes PII while still enabling uniqueness counting. |
| A4 | **Real-time vs batch**? | synchronous; near-real-time async; nightly batch | **Near-real-time, async.** Clicks are captured off the redirect path so analytics never adds latency to (or can fail) a redirect. Aggregation is computed on read. |
| A5 | **Time bucketing / timezone** | server local; UTC; per-viewer | **UTC day buckets.** Deterministic and unambiguous; the caller can re-bucket. Stated in the API. |
| A6 | Which **dimensions**? | total; unique; by day; by referrer; by country/device | **total, unique, by-day (windowed), top referrers.** Country/device (geo-IP, UA parsing) deferred — they add dependencies and PII surface without being core. |
| A7 | **Durability vs performance** of capture | write every event; sample; counter-only | **Write every event** (row per click) + aggregate on read. Simple and correct at prototype scale; sampling/rollups noted as the scaling path. |
| A8 | **Failure semantics** | block redirect on analytics failure; drop | **Drop + log.** A redirect must succeed even if analytics is down. Fire-and-forget with error logging. |

## Normalized spec

- **Capture:** on a successful redirect, asynchronously record `{ short_code, occurred_at (UTC),
  ip_hash, user_agent, referer }`. Never block or fail the redirect.
- **Store:** `click_events` keyed by `short_code` (decoupled from the link row, so it also works on
  the Redis cache-hit path where we don't load the entity). Index `(short_code, occurred_at)`.
- **Read:** `GET /api/v1/links/{code}/stats?days=N` → `{ totalClicks, uniqueVisitors,
  clicksByDay[], topReferrers[] }`. 404 if the code doesn't exist.
- **Privacy:** raw IP is never persisted; only `HMAC-SHA256(ip, app.analytics.ip-salt)`.
- **Retention:** configurable max age (`app.analytics.retention`); older events are purgeable
  (sweep implemented as a scheduled task; see limitations for what's deferred).

## Risks / limitations (carried to the risk register)

- **R8 (PII):** mitigated by hashing; residual is that a salted hash is still linkable within the
  salt epoch — acceptable for counting, documented.
- No **bot filtering** → counts include crawlers/prefetchers.
- Unique-visitor is an **estimate** (IP hash), not identity.
- Aggregation is **on-read SQL**; at high volume this moves to periodic rollups (noted).
