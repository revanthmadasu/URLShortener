# Scenario 1 — Greenfield: build the core link service

Building a new system from nothing, with clear (well-defined) requirements.

## Requirement (normalized)

Provide shorten / redirect / fetch / delete over a durable store, with input validation, a
clean error contract, and a test suite — the working core of the prototype.

## Decomposition (executed in order)

1. **Schema** (Flyway `V1`): `links` with a **unique index on `short_code`** as the single
   collision arbiter; `expires_at` nullable; a hashed `management_token`.
2. **Persistence**: `Link` entity + `LinkRepository`.
3. **Code generation** (v1): `RandomShortCodeGenerator` — deliberately concrete, to give the
   Phase 2 refactor a real seam.
4. **Validation**: `UrlValidator` (scheme allowlist); Bean Validation on the request DTO.
5. **Security**: per-link **management token** — issued once, stored as SHA-256, constant-time
   verified. Keeps an "open prototype" from having a trivially abusable `DELETE`.
6. **Service**: `LinkService` — insert-and-catch retry (never check-then-insert); custom-alias
   conflict → 409; expiry via an injected `Clock`.
7. **Web**: `LinkController` + `RedirectController` (302 + no-cache, so clicks stay observable);
   RFC 9457 `application/problem+json` errors via one `@RestControllerAdvice`.

## Execution & validation

- 43 unit tests (pure, Docker-free) + a Testcontainers `LinkFlowIT` covering the full HTTP flow
  and a **16-thread concurrent custom-alias race** asserting exactly one winner.
- The integration run caught a real **`CHAR` vs `VARCHAR`** schema/entity mismatch that mocks
  could not — see the traceability log.

## Key decisions

- **Database is the arbiter** of uniqueness (unique index), not application checks — correct
  under concurrency.
- **302, not 301**, so the redirect stays observable for analytics (documented trade-off).
- **Injected `Clock`** so expiry logic is deterministically testable.
