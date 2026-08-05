# Testing Approach, Limitations & Trade-offs

## Test pyramid

| Layer | What | Runs when | Docker? |
|---|---|---|---|
| **Unit** (`*Test`) | Pure logic: code generation (Feistel bijection), URL/SSRF validation, token hashing, cache degradation + circuit breaker, rate limiting, service orchestration with mocks, web slices (`@WebMvcTest`) | every `mvn test` / push | No |
| **Integration** (`*IT`) | Full request path against **real Postgres + Redis** via Testcontainers: end-to-end flow, concurrent alias race, cache-aside behavior, async analytics, rate-limit 429 | `mvn verify` | Yes |
| **Mutation** (opt-in) | PIT mutation testing over codec/service/common — measures test *effectiveness*, not just line coverage | `mvn -Pmutation test` | No |
| **Load** (manual) | k6 script on the redirect hot path with p95/p99 budgets | manual (`perf/redirect-load.js`) | app running |

Current coverage: **~94% line / ~79% branch** (JaCoCo). The build **gates** on ≥75% line
coverage, google-java-format (Spotless), and all tests.

## Why this split

- **Unit tests are hermetic and fast** — no Docker, no network, deterministic clocks injected
  everywhere time matters. `mvn test` is the fast inner loop and the required PR gate.
- **Integration tests use the real datastores**, not H2 or fakes, because the most important
  behaviors are database-arbitrated: the unique-index alias race, Flyway schema validation
  (which caught a real `CHAR` vs `VARCHAR` bug), and native aggregation SQL. Mocks cannot verify
  these; only a real Postgres can.
- **Concurrency is tested for real**: `LinkFlowIT` fires 16 simultaneous creates of the same
  alias and asserts exactly one winner.
- **Async is awaited, not slept**: analytics capture is verified with Awaitility polling.

## Quality gates

Enforced on every `mvn verify` (and in CI):

- **Spotless / google-java-format** — consistent formatting; `mvn spotless:apply` to fix.
- **JaCoCo** — coverage report + a ≥75% line-coverage gate (excludes config/dto/entrypoint).
- **Unit + integration tests** — Surefire + Failsafe.

Opt-in profiles (heavier; run nightly/on-demand rather than per-PR):

- `-Pspotbugs` — static bug analysis.
- `-Pmutation` — PIT mutation testing (threshold 70%).
- `-Psecurity` — OWASP dependency-check (needs `NVD_API_KEY`, slow first run).

## Limitations & trade-offs (honest)

- **Rate limiting is per-instance, in-memory** — across N app instances the effective limit is
  N×, and it resets on restart. Distributed limiting needs a shared store (Redis). See R2.
- **SSRF guard is defense-in-depth**, evaluated at creation time; it cannot stop DNS rebinding,
  and the service redirects the client rather than fetching. See R3.
- **Analytics uniqueness is an estimate** (salted IP hash), with no bot filtering; aggregation
  is on-read SQL, which would move to rollups at high volume. See ADR-0004.
- **Integration tests require Docker**; they are excluded from `mvn test` so contributors without
  Docker still get a fast, green unit build.
- **Load testing is manual** — the k6 script is provided but not wired into CI (needs a running
  stack and the k6 binary).
- **No contract/OpenAPI tests yet** — the API shape is covered by web-slice assertions, not a
  published schema. A natural next step.
