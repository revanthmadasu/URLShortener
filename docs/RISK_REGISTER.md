# Risk Register & Guardrails

Living document. Each risk records likelihood/impact, the mitigation/guardrail in the design,
and whether the residual risk is **Accepted** (a conscious trade-off) or **Mitigated**.

| # | Risk | L×I | Mitigation / Guardrail | Status |
|---|------|-----|------------------------|--------|
| R1 | **No user auth** — management APIs are open | H×M | Per-link **management token** required for mutation (`DELETE`); rate limiting on writes. Redirects are intentionally public. | Accepted (prototype scope) |
| R2 | **Rate limiting is IP-only** without auth — bypassable via IP rotation | M×M | Token-bucket per IP as a first line; documented as insufficient for a hostile actor. Real fix = API keys (out of chosen scope). | Accepted |
| R3 | **Open redirect / SSRF** — attacker shortens a link to an internal address | M×H | Scheme allowlist + `PrivateNetworkGuard` blocks hosts resolving to private/loopback/link-local (incl. `169.254.169.254`)/multicast/IPv6-ULA; fails closed on unresolvable hosts. **Residual:** we 302 the client (not a server-fetch), and creation-time checks can't stop DNS rebinding — defense-in-depth, documented. | Mitigated (Phase 2C) |
| R4 | **Redis outage** takes down redirects | M×H | Cache-aside with graceful fallback to Postgres; Redis excluded from health/liveness; circuit breaker to avoid piling up on a dead cache. | Mitigated (Phase 2) |
| R5 | **Custom-alias race** — two concurrent creates for same alias | M×M | DB unique constraint is the arbiter; catch constraint violation → 409. Never "check-then-insert" in app code alone. | Mitigated (Phase 1/2) |
| R6 | **Short-code collision** as keyspace fills | L×M | Base62 length sized for headroom; Phase 2 switches to sequence+Feistel (collision-free by construction). | Mitigated (Phase 2) |
| R7 | **Unbounded redirect scans** probing random codes | M×L | Negative cache for "not found"; rate limiting; codes are non-sequential to the client. | Mitigated |
| R8 | **PII in analytics** (raw IPs) | M×M | Store a salted hash for uniqueness, not raw IP; define retention. Decided in Phase 3 ambiguity register. | Planned (Phase 3) |
| R9 | **AI-introduced defect** slips into main | M×H | Quality gates (Spotless, SpotBugs, tests, JaCoCo, dep-check, mutation testing) + human sign-off on high-impact changes; contemporaneous traceability log. | Mitigated (Phase 4) |
| R10 | **Expired links** still resolve | L×M | Expiry checked on read; expired → 410 Gone; excluded from cache or cached with short TTL. | Planned (Phase 1) |

## Validation strategy (summary)

- **Correctness:** unit tests for code-gen, validation, expiry, token checks; integration
  tests against real Postgres/Redis for the full request path and the alias-race.
- **Security:** SSRF/open-redirect unit tests with hostile inputs; OWASP dependency-check in CI.
- **Reliability:** cache-outage test (redirect still succeeds with Redis stopped);
  circuit-breaker behavior test.
- **Performance:** k6 load script on the redirect path; JaCoCo + PIT mutation testing to
  gauge test *effectiveness*, not just coverage.
