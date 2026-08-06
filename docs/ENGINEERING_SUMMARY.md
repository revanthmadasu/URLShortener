# Final Engineering Summary

A production-grade URL shortener, built **AI-assisted with the engineer owning every decision**
and the AI executing within task boundaries. This document ties the work together:
**plan/rationale, artifacts, risks/trade-offs/validation, assumptions, and limitations.**

---

## 1. Plan & rationale

**Requirement understanding.** The brief's title says "AI-Assisted Software Engineering System,"
but §2/§5 describe a URL shortener as the deliverable. The ambiguity was surfaced and resolved
(with sign-off) as **Interpretation A: build the URL shortener using AI assistance and document
the process rigorously** — §4's items (traceability, quality gates, decomposition) are treated as
*how we work*, not features to code. Stack, storage, and auth posture were engineer-selected.

**Why this stack (ADR-0001).** Java 21 + Spring Boot 4, **Postgres as source of truth** + **Redis
as hot cache**. A shortener is overwhelmingly redirect-read traffic with a correctness-critical
write path (custom-alias uniqueness), so a transactional store fronted by a cache is the natural
fit; Java 21 virtual threads suit the blocking-I/O redirect path.

**Execution model.** Work was decomposed into phases; each phase is task-specified
(intent / constraints / acceptance criteria / technical context), implemented, tested (unit +
Testcontainers), gated, and **committed separately** for reviewability.

| Phase | Outcome | Commit(s) |
|---|---|---|
| 0 | Scaffold, Docker Compose, CI, docs skeleton | `9ac3b0c` |
| 1 — **Greenfield** | Core link service (create / redirect / get / delete) | `4ab42db` |
| 2 — **Brownfield** | Feistel code-gen refactor · Redis cache-aside · SSRF fix | `212a6f4` · `4db2993` · `4486790` |
| 3 — **Ambiguous** | Click analytics from an ambiguity register | `9f04ded` |
| 4 | Rate limiting, observability, enforced quality gates | `490b396` |
| 5 | Architecture overview + engineering summary | `efd6a93` |
| + | Static API tester UI + live latency SLI chart | `6a2d00b` · `c64655f` · `c83fdc5` |

## 2. Artifacts (deliverables)

**Working prototype (runnable end-to-end):**
- REST API — `POST /api/v1/links`, `GET /{code}` (302), `GET /api/v1/links/{code}`,
  `DELETE /api/v1/links/{code}`, `GET /api/v1/links/{code}/stats?days=N`.
- **Browser UI** served at `/` — a dependency-free tester for every endpoint, plus a **live
  average-response-time chart** driven by a purpose-built `urlshortener.redirect.latency` SLI.
- `docker-compose.yml` (Postgres + Redis), `Makefile`, GitHub Actions CI, Maven wrapper.

**Code & schema:** ~40 Java classes in a package-by-feature layout (`link/`, `analytics/`,
`common/`, `config/`); 3 Flyway migrations; RFC 9457 `problem+json` error contract; typed
`AppProperties` config.

**Tests:** 101 unit + 6 integration (Testcontainers) — see §4.

**Documentation:**
- [Architecture overview](ARCHITECTURE.md) (component + control-flow diagram)
- 4 [ADRs](adr/) (stack, code generation, caching, analytics)
- 3 scenario write-ups: [greenfield](scenarios/greenfield.md) · [brownfield](scenarios/brownfield.md) · [ambiguous](scenarios/ambiguous.md)
- [AI traceability log](AI_TRACEABILITY.md) (generated / edited / **rejected** with rationale)
- [Risk register](RISK_REGISTER.md) · [Testing approach](TESTING.md) · this summary

## 3. The three required scenarios

- **Greenfield** — built the core from nothing: schema with the unique index as the collision
  arbiter, service with insert-and-catch retry, validation, error model, tests.
- **Brownfield** — evolved tested code without breaking it, with **impact analysis first**:
  extracted a `ShortCodeGenerator` strategy + a **Feistel** codec (collision-free, non-sequential,
  DB still the arbiter); added **cache-aside with graceful degradation** (TTL bounded by remaining
  lifetime so a cache hit can't serve an expired link); closed an **SSRF/open-redirect** gap.
- **Ambiguous** — turned "add analytics" into a spec by resolving **8 open questions** (what a
  click is, uniqueness, PII/retention, real-time vs batch, tz, dimensions, durability, failure
  semantics), then built to it (ADR-0004).

## 4. Risks, trade-offs & validation

**Validation (rigor):**
- **101 unit tests** — hermetic, no Docker, deterministic (injected `Clock` everywhere time
  matters); property tests prove the Feistel codec is bijective; hostile-input tests for SSRF.
- **6 integration tests** — real Postgres + Redis via Testcontainers: full flow, a **16-thread
  concurrent alias race** (asserts exactly one winner), cache-aside survival after out-of-band
  row deletion, async analytics (Awaitility), and rate-limit 429.
- **Coverage: ~94% line / ~79% branch** (JaCoCo).
- **Enforced gates** on `mvn verify` / CI: Spotless (google-java-format), JaCoCo ≥75% line, all
  tests. **Opt-in profiles:** SpotBugs, PIT mutation testing, OWASP dependency-check.
- **Manual load:** k6 script (`perf/redirect-load.js`) with p95/p99 budgets.

**Key trade-offs made (with rationale):**
- **302 over 301** — accepts a little latency so every click reaches the server (analytics);
  mitigated by the Redis cache.
- **Small hand-rolled circuit breaker / rate limiter** over Resilience4j/Bucket4j — avoids
  unvetted Spring-Boot-4 dependencies; the behavior is small and clock-testable.
- **On-read SQL aggregation** for analytics — simple and correct at prototype scale; the scaling
  path (rollups) is documented rather than pre-built.

**Risk register** ([full table](RISK_REGISTER.md)) — 10 tracked risks, each with a mitigation and
an explicit *Accepted* / *Mitigated* status, e.g. R2 (IP-only rate limiting under no-auth),
R3 (SSRF as defense-in-depth), R4 (Redis outage → degrade, not fail), R8 (PII → salted hash +
retention).

**AI-assisted quality control** — defects caught by the compiler/tests, not prose, and logged:
a Boot-4 API package move, a non-generic `PostgreSQLContainer`, a `CHAR` vs `VARCHAR` mismatch
(only real Postgres finds it), a **NUL-byte** cache sentinel, a 500-instead-of-400 validation
gap, a cross-IT isolation bug introduced by analytics, and an **observer effect** in the latency
chart (fixed with a dedicated redirect-only timer).

### Verified live end-to-end

```text
POST /api/v1/links {"url":"https://example.com/some/very/long/path?a=1&b=2"}
 -> 201  shortCode "Md2Wh2u" (Feistel: non-sequential) + one-time managementToken
GET /Md2Wh2u                       -> 302  Location: https://example.com/...
GET /api/v1/links/Md2Wh2u/stats    -> { totalClicks: 5, uniqueVisitors: 4,
                                        topReferrers: [a×3, (none)×1, b×1] }
POST /api/v1/links {"url":"http://169.254.169.254/latest/meta-data/"}
 -> 400  problem+json "host resolves to a disallowed (private/internal) address"
DELETE /api/v1/links/Md2Wh2u                    -> 403 (no token)
DELETE .../Md2Wh2u  X-Management-Token: <token> -> 204
GET /Md2Wh2u (after delete)                     -> 404
```

## 5. Assumptions

- Interpretation A (shortener + process docs); Java 21 + Spring Boot; Postgres + Redis; an
  **open (no-auth) prototype** — all engineer-selected.
- No-auth is compensated by a **per-link management token** (a capability, not identity), so the
  destructive endpoint isn't trivially abusable.
- Deployment behind a trusted proxy for `X-Forwarded-For`; **UTC** for all time bucketing.
- Short-code keyspace `62^7 ≈ 3.5×10¹²` is ample; exhaustion fails cleanly rather than looping.

## 6. Limitations (and the path forward)

- **Rate limiting** is per-instance / in-memory → a Redis-backed limiter for multi-instance.
- **Auth** is intentionally absent → API keys would add per-tenant limits and ownership.
- **Analytics** uniqueness is a salted-IP estimate with no bot filtering; on-read aggregation
  → periodic rollups at scale.
- **SSRF** guard is creation-time and defense-in-depth; it can't stop DNS rebinding.
- **No published OpenAPI contract** yet (API shape is covered by web-slice tests).
- **The tester UI is not covered by automated tests** — it's a static convenience page; the API
  it exercises is fully tested.

## 7. How to run

```bash
docker compose up -d      # Postgres + Redis
make run                  # or: java -jar target/urlshortener-0.0.1-SNAPSHOT.jar
# then open http://localhost:8080/  (UI)   ·   make test / make verify  (tests)
```

Full setup, testing strategy, and trade-offs are in [TESTING.md](TESTING.md); component and
control-flow detail in [ARCHITECTURE.md](ARCHITECTURE.md).
