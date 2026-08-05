# AI-Assisted Execution — Traceability Log

> **Purpose.** This log makes the AI-assisted workflow auditable, as required by the
> assignment's §4 ("maintain traceability: generated / edited / rejected with rationale").
> The **engineer owns every decision**; the AI assistant (Claude) executes within
> engineer-defined tasks. Each task records **intent, constraints, acceptance criteria,
> technical context**, and the disposition of AI output (**accepted / edited / rejected**)
> with rationale. High-impact changes carry an explicit **human sign-off** marker.

**Legend** — Disposition of AI-proposed output:
`✅ Accepted` · `✏️ Edited` (accepted after human modification) · `❌ Rejected` (with rationale) · `🚦 Sign-off` (human approval gate for high-impact change)

---

## Task 0 — Framing & scope normalization *(requirement understanding)*

**Intent.** Resolve the ambiguity in the brief before writing code: is the deliverable the
URL shortener built *with* AI (documented rigorously), or a generic AI-engineering harness?

**Constraints.** Must satisfy §5's explicit deliverable list; must be runnable end-to-end.

**Acceptance criteria.** A single, written interpretation the engineer signs off on.

**Execution.**
- AI surfaced two readings (A: shortener + process docs; B: build a harness) and
  recommended **A**, citing §5 ("working prototype, runnable end-to-end") and §2 ("build a
  URL shortener service from scratch").
- 🚦 **Sign-off:** Engineer selected **A** (shortener + process docs).
- Stack decisions (engineer-selected): **Java 21 + Spring Boot**, **Postgres + Redis via
  Docker Compose**, **no user auth** (open prototype).

**Rationale captured for later scrutiny:** "no auth" weakens the security narrative, so the
AI proposed a compensating control (per-link management token) rather than silently shipping
an unauthenticated destructive endpoint. See Task 0.1.

---

## Task 0.1 — Compensating control for the "no auth" decision *(risk-driven design)*

**Intent.** Keep the "open prototype" decision while not shipping an obviously abusable
`DELETE /links/{code}` (anyone could delete anyone's link).

**Options weighed.**
1. Ship DELETE fully open — ❌ rejected: trivial destructive abuse, poor §6 security story.
2. Add full auth — ❌ rejected: contradicts the engineer's explicit "no auth" decision.
3. **Per-link capability token** returned once at creation, required for mutation — ✅ chosen.

**Disposition:** ✅ Accepted by engineer as the default (`app.security.require-management-token=true`),
config-toggleable. Documented as a capability token, **not** user identity.

---

## Task 0.2 — Project scaffold *(greenfield setup)*

**Intent.** Generate a Spring Boot skeleton with the agreed dependency set.

**Technical context.** No local `mvn`/`gradle`; JDK 17 present, JDK 21 required.

**Execution & notable corrections (the value of contemporaneous logging):**

| Event | Disposition | Rationale |
|---|---|---|
| First Initializr call pinned Spring Boot `3.3.5` | ❌ Rejected by tooling | Initializr now requires `>=4.0.0`. AI queried live metadata instead of guessing a version. |
| Re-generated on Boot **4.1.0 / Java 21** | ✅ Accepted | Latest stable per Initializr metadata; Java 21 unlocks virtual threads. |
| AI "improved" `PostgreSQLContainer` to a generic `PostgreSQLContainer<?>` | ❌ Rejected by compiler | In this Testcontainers version the class is **non-generic**; reverted to the raw type. A reminder that AI pattern-matching on older APIs must be checked by the build. |
| Reclassified generated `*Tests` context-load test to `*IT` | ✏️ Edited | So `./mvnw test` (unit) stays green without a Docker daemon; Docker-dependent tests run under Failsafe. |
| Installed `openjdk@21` via Homebrew (keg-only) | ✅ Accepted | No sudo; leaves system JDK 17 untouched. `JAVA_HOME` passed per-build. |

**Quality gate at Phase 0:** `./mvnw test` → BUILD SUCCESS (0 unit tests yet, IT excluded from
Surefire). Toolchain proven on the real target runtime before feature work began.

🚦 **Sign-off:** Engineer approved the scaffold and infra choices.

---

## Task 1 — Greenfield core link service *(scenario 1)*

**Intent.** Implement create / redirect / fetch / delete with a durable schema, input
validation, structured errors, and a test suite — the working core of the prototype.

**Constraints.** Postgres owns the schema (Flyway; Hibernate `validate` only). No
check-then-insert. Errors as RFC 9457 problem+json. Redirects must remain observable for
future analytics. Unit tests must run without Docker.

**Acceptance criteria.**
- `POST /api/v1/links` → 201 + one-time management token; `GET /{code}` → 302; metadata via
  `GET /api/v1/links/{code}`; `DELETE` guarded by the management token (403 without).
- Invalid/dangerous URLs and past expiries rejected with 400; unknown → 404; expired → 410.
- `./mvnw test` green with no external services.

**Decomposition (executed in order).**
1. Flyway `V1` schema (unique index on `short_code` as the collision arbiter).
2. `Link` entity + `LinkRepository`.
3. `RandomShortCodeGenerator` (naive v1, concrete — seam for Phase 2 refactor).
4. `UrlValidator` (scheme allowlist; SSRF gap left for Phase 2, tracked R3).
5. `ManagementTokenService` (issue once, store SHA-256, constant-time verify).
6. `LinkService` (insert-and-catch retry; alias-conflict → 409; expiry via injected `Clock`).
7. Controllers + `GlobalExceptionHandler` (problem+json).
8. Tests: 43 unit tests + a Testcontainers `LinkFlowIT` (full flow + concurrent alias race).

**Notable dispositions.**

| Item | Disposition | Rationale |
|---|---|---|
| `@WebMvcTest` import `...boot.test.autoconfigure.web.servlet` | ❌ Rejected by compiler | Boot 4 moved it to `...boot.webmvc.test.autoconfigure`. AI located the class in the jar and corrected the import rather than guessing. |
| `Link.create(...)` calling `Instant.now()` internally | ✏️ Edited | Refactored to accept `createdAt` from the service's injected `Clock` — deterministic tests, single clock source. |
| Retry loop inside an outer `@Transactional` | ❌ Rejected in design | A constraint violation poisons the surrounding transaction. Chose repository-per-call transactions so each attempt rolls back independently. Documented in `LinkService` Javadoc. |
| 301 vs 302 for redirect | ✏️ Chose 302 + no-cache | 301 is cached by browsers and would hide clicks from analytics. Trade-off documented in `RedirectController`. |
| Unused `HttpEntity`/`HttpMethod` imports in IT (AI first added a `@SuppressWarnings` guard) | ❌ Rejected | The guard was a smell; removed the imports outright. |
| Migration column `management_token_hash CHAR(64)` | ✏️ Edited → `VARCHAR(64)` | **Caught only by the integration test**, not units: Hibernate `ddl-auto=validate` rejected `bpchar` vs the entity's expected `varchar`. `VARCHAR` is also more correct — `CHAR` space-pads to a fixed width. A concrete case for testing against the real database, not just mocks. |

**Quality gate:** `./mvnw verify` → **43 unit + 3 integration passed, 0 failed.** The
`concurrentSameAliasYieldsExactlyOneWinner` IT fires 16 simultaneous creates of the same alias
against real Postgres and asserts exactly one 201 and fifteen 409s — proving the unique index,
not application code, arbitrates the race.

🚦 **Sign-off:** Engineer to review commit; end-to-end verified green.

<!-- Subsequent tasks appended per phase. -->

