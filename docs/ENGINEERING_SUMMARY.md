# Final Engineering Summary

A production-grade URL shortener built AI-assisted, with the engineer owning every decision and
the AI executing within task boundaries. This document ties the work together: plan and
rationale, artifacts, validation, risks/trade-offs, assumptions, and limitations.

## 1. Plan & rationale

The brief was interpreted (with sign-off) as **build the URL shortener using AI assistance and
document the process rigorously** — §4's items are treated as *how we work*, not features. Work
was decomposed into phases, each task-specified and committed separately for reviewability:

| Phase | Outcome | Commit |
|---|---|---|
| 0 | Scaffold, Docker Compose, CI, docs skeleton | `9ac3b0c` |
| 1 — **Greenfield** | Core link service (create/redirect/get/delete) | `4ab42db` |
| 2 — **Brownfield** | Feistel code-gen refactor, Redis cache-aside, SSRF fix | `212a6f4`,`4db2993`,`4486790` |
| 3 — **Ambiguous** | Click analytics from an ambiguity register | `9f04ded` |
| 4 | Rate limiting, observability, enforced quality gates | `490b396` |
| 5 | Architecture overview + this summary | (docs) |

Rationale for the big choices is in the [ADRs](adr/); the stack is Java 21 + Spring Boot 4,
Postgres (source of truth) + Redis (hot cache), chosen for a redirect-heavy, reliability-focused
service (ADR-0001).

## 2. The three required scenarios

- **Greenfield** ([scenario](scenarios/README.md)) — built the core from nothing: schema,
  service, validation, error model, 43 tests.
- **Brownfield** ([impact analysis](scenarios/brownfield.md)) — evolved tested code without
  breaking it: extracted a `ShortCodeGenerator` strategy and added a **Feistel** codec
  (collision-free, non-sequential), added **cache-aside with graceful degradation**, and closed
  an **SSRF/open-redirect** gap — each a separate reviewable commit with impact analysis.
- **Ambiguous** ([ambiguity register](scenarios/ambiguous.md)) — turned "add analytics" into a
  spec by resolving 8 open questions (what a click is, uniqueness, PII/retention, real-time vs
  batch, tz, dimensions, durability, failure semantics), then built to it (ADR-0004).

## 3. AI-assisted execution (the differentiator)

Every task carried **intent, constraints, acceptance criteria, and technical context**, and the
disposition of AI output (**accepted / edited / rejected**) was logged contemporaneously in
[AI_TRACEABILITY.md](AI_TRACEABILITY.md). Highlights that show engineer-owned quality control:

- **Defects the tests/compiler caught, not the prose:** a Boot-4 API package move
  (`@WebMvcTest`), a non-generic `PostgreSQLContainer`, a `CHAR` vs `VARCHAR` schema mismatch
  (only visible against real Postgres), a **NUL-byte** cache sentinel, a 500-instead-of-400 on
  param validation, and a real **cross-IT isolation bug** introduced by adding analytics.
- **Dependencies deliberately rejected** (Resilience4j/Bucket4j) in favor of small,
  clock-testable implementations, avoiding unvetted Boot-4 compatibility risk.
- **Honest scoping** over overclaiming — e.g. the SSRF guard is documented as defense-in-depth
  (the service redirects the client, doesn't fetch) with the DNS-rebinding residual stated.

## 4. Validation & risk control

- **Tests:** 101 unit (hermetic, no Docker) + 6 integration (real Postgres/Redis via
  Testcontainers), incl. a 16-thread concurrent alias race and async analytics. **~94% line /
  ~79% branch** coverage.
- **Enforced gates** (`mvn verify` / CI): Spotless formatting, JaCoCo ≥75% line coverage, all
  tests. **Opt-in**: SpotBugs, PIT mutation testing, OWASP dependency-check.
- **Risk register:** [RISK_REGISTER.md](RISK_REGISTER.md) — 10 risks with mitigations and
  explicit *accepted* residuals (e.g. IP-only rate limiting under the no-auth decision).

## 5. Live end-to-end (verified)

```text
POST /api/v1/links {"url":"https://example.com/some/very/long/path?a=1&b=2"}
 -> 201  shortCode "Md2Wh2u"  (Feistel: non-sequential)  + one-time managementToken
GET /Md2Wh2u                       -> 302  Location: https://example.com/...
GET /api/v1/links/Md2Wh2u/stats    -> { totalClicks: 5, uniqueVisitors: 4,
                                        topReferrers: [a×3, (none)×1, b×1] }
POST /api/v1/links {"url":"http://169.254.169.254/latest/meta-data/"}
 -> 400  problem+json "host resolves to a disallowed (private/internal) address"
DELETE /api/v1/links/Md2Wh2u                         -> 403 (no token)
DELETE .../Md2Wh2u  X-Management-Token: <token>      -> 204
GET /Md2Wh2u  (after delete)                         -> 404
```

## 6. Assumptions

- Interpretation A (shortener + process docs), Java 21 + Spring Boot, Postgres + Redis, and an
  **open (no-auth) prototype** were engineer-selected decisions.
- No-auth is compensated by a **per-link management token** (a capability, not identity) so the
  destructive endpoint isn't trivially abusable.
- Deployment behind a trusted proxy for `X-Forwarded-For`; UTC for all time bucketing.

## 7. Limitations (and the path forward)

- **Rate limiting** is per-instance/in-memory → move to a Redis-backed limiter for multi-instance.
- **Auth** is intentionally absent → API keys would enable per-tenant limits and ownership.
- **Analytics** uniqueness is a salted-IP estimate with no bot filtering; on-read aggregation
  would become periodic rollups at scale.
- **SSRF** guard can't stop DNS rebinding; a fetch-time re-check would be needed if the service
  ever fetched URLs.
- **No published OpenAPI contract** yet; API shape is covered by web-slice tests.

## 8. How to run

See the [README](../README.md): `docker compose up -d && make run`, then the examples above.
Full setup, testing strategy, and trade-offs are in [TESTING.md](TESTING.md).
