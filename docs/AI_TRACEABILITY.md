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

<!-- Subsequent tasks appended per phase. -->
