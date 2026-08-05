package com.example.urlshortener.common.error;

import org.springframework.http.HttpStatus;

/** Concrete API exceptions, grouped for discoverability. */
public final class Errors {

  private Errors() {}

  /** 404 — no link for the requested code. */
  public static final class NotFound extends ApiException {
    public NotFound(String detail) {
      super(HttpStatus.NOT_FOUND, "link-not-found", detail);
    }
  }

  /** 409 — the requested custom alias is already taken. */
  public static final class AliasConflict extends ApiException {
    public AliasConflict(String alias) {
      super(
          HttpStatus.CONFLICT, "alias-conflict", "Custom alias '" + alias + "' is already in use");
    }
  }

  /** 409 — code generation could not find a free code within the retry budget. */
  public static final class CodeExhausted extends ApiException {
    public CodeExhausted(String detail) {
      super(HttpStatus.CONFLICT, "code-generation-exhausted", detail);
    }
  }

  /** 400 — the destination URL is missing, malformed, or uses a disallowed scheme/host. */
  public static final class InvalidUrl extends ApiException {
    public InvalidUrl(String detail) {
      super(HttpStatus.BAD_REQUEST, "invalid-url", detail);
    }
  }

  /** 400 — the requested expiry is invalid (e.g. in the past). */
  public static final class InvalidExpiry extends ApiException {
    public InvalidExpiry(String detail) {
      super(HttpStatus.BAD_REQUEST, "invalid-expiry", detail);
    }
  }

  /** 403 — a valid management token is required and was missing or incorrect. */
  public static final class Forbidden extends ApiException {
    public Forbidden(String detail) {
      super(HttpStatus.FORBIDDEN, "forbidden", detail);
    }
  }

  /** 410 — the link existed but has expired. */
  public static final class Gone extends ApiException {
    public Gone(String detail) {
      super(HttpStatus.GONE, "link-expired", detail);
    }
  }

  /** 429 — the client has exceeded the request rate limit. */
  public static final class RateLimited extends ApiException {
    public RateLimited(String detail) {
      super(HttpStatus.TOO_MANY_REQUESTS, "rate-limited", detail);
    }
  }
}
