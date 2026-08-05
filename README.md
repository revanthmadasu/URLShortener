# URL Shortener

A production-grade URL shortener service built with **Java 21 + Spring Boot 4**, backed by
**Postgres** (source of truth) and **Redis** (hot-path redirect cache). Built as an
interview assignment demonstrating **AI-assisted software engineering** — see
[`docs/`](docs/) for the architecture overview, decision records, scenario write-ups, and the
[AI traceability log](docs/AI_TRACEABILITY.md).

> **Status:** Phase 0 complete (scaffold, infra, CI). Feature work in progress — see
> [`docs/AI_TRACEABILITY.md`](docs/AI_TRACEABILITY.md) for the live execution log.

## Features (target)

- `POST /api/v1/links` — shorten a URL (with optional custom alias + expiry)
- `GET /{code}` — fast redirect (Redis cache-aside → Postgres)
- `GET /api/v1/links/{code}` — link metadata
- `DELETE /api/v1/links/{code}` — delete (guarded by a per-link management token)
- `GET /api/v1/links/{code}/stats` — click analytics
- Rate limiting, structured errors (RFC 9457 `application/problem+json`), health/metrics.

## Prerequisites

- **JDK 21** (a keg-only Homebrew `openjdk@21` works; the `Makefile` auto-detects it)
- **Docker** (for Postgres + Redis, and for integration tests via Testcontainers)

## Quick start

```bash
# 1. Start Postgres + Redis
docker compose up -d

# 2. Run the app (Flyway applies the schema on boot)
make run          # or: ./mvnw spring-boot:run

# 3. Health check
curl -s localhost:8080/actuator/health
```

If you are not on macOS/Homebrew, point `JAVA_HOME` at any JDK 21 before running Maven:

```bash
export JAVA_HOME=/path/to/jdk-21
./mvnw spring-boot:run
```

## Testing

```bash
make test     # unit tests only — no Docker required
make verify   # unit + integration (Testcontainers, needs Docker) + quality gates
```

The test strategy deliberately separates **unit tests** (`*Test`, pure, fast, run on every
push) from **integration tests** (`*IT`, real Postgres/Redis via Testcontainers, run in
`verify`). See [docs/TESTING.md](docs/TESTING.md) for the full approach, limitations, and
trade-offs.

## Documentation

| Document | What it covers |
|---|---|
| [Architecture](docs/ARCHITECTURE.md) | Components, control flow, key decisions |
| [ADRs](docs/adr/) | Architecture Decision Records |
| [Scenarios](docs/scenarios/) | Greenfield / brownfield / ambiguous walkthroughs |
| [AI Traceability](docs/AI_TRACEABILITY.md) | Task specs + generated/edited/rejected log |
| [Risk Register](docs/RISK_REGISTER.md) | Risks, trade-offs, guardrails |
| [Testing](docs/TESTING.md) | Test approach, limitations, trade-offs |

## Configuration

All knobs live under the `app.*` namespace (see
[`AppProperties`](src/main/java/com/example/urlshortener/config/AppProperties.java)) and are
overridable via environment variables (`APP_BASE_URL`, `DB_URL`, `REDIS_HOST`, …). Defaults
target the local Docker Compose stack.
