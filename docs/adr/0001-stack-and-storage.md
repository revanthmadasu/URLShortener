# ADR-0001: Language, framework, and storage

- **Status:** Accepted
- **Date:** 2026-08-05
- **Decision owner:** Engineer (AI-assisted analysis)

## Context

We are building a URL shortener that must be redirect-heavy, reliable, and horizontally
scalable, and must present a defensible production-grade design. The assignment allows any
stack. Constraints from the engineer: JVM-oriented (enterprise fit), runnable end-to-end.

## Decision

- **Java 21 + Spring Boot 4.1.** Mature ecosystem for layered services, first-class
  validation, migrations, metrics, and testing (Testcontainers). Java 21 **virtual threads**
  fit a blocking-I/O redirect service: high request concurrency without a large platform
  thread pool.
- **Postgres as the source of truth.** Strong consistency for the code→URL mapping and a
  unique constraint that makes custom-alias/collision handling correct under concurrency.
- **Redis as a hot-path cache** (cache-aside) for redirects, plus counters and rate-limit
  state. Read volume on `/{code}` dominates; caching removes the DB from the common path.

## Consequences

- **(+)** Clear separation: durability in Postgres, latency in Redis.
- **(+)** Redis is treated as *optional*: if it is down, redirects fall back to Postgres
  (degraded latency, not an outage). Redis health does **not** flip liveness (see
  `management.health.redis.enabled=false`).
- **(−)** Two stateful dependencies to operate. Mitigated by Docker Compose for local/dev and
  Testcontainers for tests.
- **(−)** Virtual threads + JDBC still consume a pooled connection per in-flight query; the
  bottleneck moves to the Hikari pool, which we size explicitly (`DB_POOL_MAX`).

## Alternatives considered

- **SQLite / in-memory:** rejected — makes the scalability/reliability story hypothetical.
- **NoSQL KV (e.g. DynamoDB) as primary:** viable for the mapping, but weaker local
  runnability and a less-illustrative transactional story for custom aliases. Rejected for
  this prototype.
