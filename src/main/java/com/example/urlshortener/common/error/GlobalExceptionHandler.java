package com.example.urlshortener.common.error;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Translates exceptions into RFC 9457 {@code application/problem+json} responses. Keeps error
 * shaping in one place so controllers and services stay free of HTTP-mapping boilerplate.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String TYPE_PREFIX = "https://errors.urlshortener.example/";

  @ExceptionHandler(ApiException.class)
  public ProblemDetail handleApiException(ApiException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    pd.setType(URI.create(TYPE_PREFIX + ex.type()));
    pd.setTitle(titleFor(ex.status()));
    return pd;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                (FieldError fe) ->
                    Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
            .toList();
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    pd.setType(URI.create(TYPE_PREFIX + "validation-failed"));
    pd.setTitle("Bad Request");
    pd.setProperty("errors", errors);
    return pd;
  }

  /** Catch-all: never leak internals. Log with a correlation-friendly message, return 500. */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex, WebRequest request) {
    log.error("Unhandled exception for request {}", request.getDescription(false), ex);
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    pd.setType(URI.create(TYPE_PREFIX + "internal-error"));
    pd.setTitle("Internal Server Error");
    return pd;
  }

  private static String titleFor(HttpStatus status) {
    return status.value() + " " + status.getReasonPhrase();
  }
}
