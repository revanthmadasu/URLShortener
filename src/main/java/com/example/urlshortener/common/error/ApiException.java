package com.example.urlshortener.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for expected, client-facing errors. Each carries the HTTP status, a short
 * machine-readable {@code type} slug (used to build an {@code about:blank}-style problem type URI),
 * and a human-readable detail. Rendered as RFC 9457 {@code application/problem+json} by {@link
 * GlobalExceptionHandler}. Using a typed hierarchy keeps HTTP concerns out of the service logic —
 * the service throws domain exceptions; the web layer maps them.
 */
public abstract class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String type;

  protected ApiException(HttpStatus status, String type, String detail) {
    super(detail);
    this.status = status;
    this.type = type;
  }

  public HttpStatus status() {
    return status;
  }

  public String type() {
    return type;
  }
}
