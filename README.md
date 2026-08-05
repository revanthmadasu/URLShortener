# URL Shortener

A production-grade URL shortener service built with **Java 21 + Spring Boot 4**, backed by
**Postgres** (source of truth) and **Redis** (hot-path redirect cache). Built as an
interview assignment demonstrating **AI-assisted software engineering** — see
[`docs/`](docs/) for the architecture overview, decision records, scenario write-ups, and the
[AI traceability log](docs/AI_TRACEABILITY.md).

> **Status:** Complete. Core service, brownfield refactor/caching/security, analytics,
> reliability + quality gates, and full documentation. **101 unit + 6 integration tests**,
> ~94% line coverage. See [`docs/ENGINEERING_SUMMARY.md`](docs/ENGINEERING_SUMMARY.md) for the
> full write-up and [`docs/AI_TRACEABILITY.md`](docs/AI_TRACEABILITY.md) for the execution log.

## Features

- `POST /api/v1/links` — shorten a URL (optional custom alias + expiry); returns a one-time
  management token
- `GET /{code}` — fast 302 redirect (Redis cache-aside → Postgres, graceful degradation)
- `GET /api/v1/links/{code}` — link metadata
- `DELETE /api/v1/links/{code}` — delete (guarded by the per-link management token)
- `GET /api/v1/links/{code}/stats?days=N` — click analytics (total, unique, by-day, referrers)
- Collision-free short codes (sequence + Feistel), SSRF/private-network guard, structured
  errors (RFC 9457 `application/problem+json`), health/metrics.

### Example

```bash
# Create
curl -s -XPOST localhost:8080/api/v1/links \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/some/long/path"}'
# -> 201 { "link": { "shortCode": "...", "shortUrl": "http://localhost:8080/..." },
#          "managementToken": "..." }

# Redirect
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' localhost:8080/<code>

# Stats
curl -s localhost:8080/api/v1/links/<code>/stats?days=7
```

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
| [Engineering Summary](docs/ENGINEERING_SUMMARY.md) | Plan, rationale, validation, assumptions, limitations |
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
