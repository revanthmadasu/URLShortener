package com.example.urlshortener.link;

import com.example.urlshortener.analytics.ClickAnalyticsService;
import com.example.urlshortener.analytics.ClickContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the public redirect: {@code GET /{code}} → 302 to the destination.
 *
 * <p><b>Why 302 (Found), not 301 (Moved Permanently).</b> A 301 is aggressively cached by browsers
 * and intermediaries, which would prevent the service from observing subsequent clicks — fatal for
 * the analytics feature. A 302 keeps the code reaching the server on every hit. We also mark the
 * response non-cacheable for the same reason. The hot-path Redis cache keeps the per-redirect cost
 * low.
 *
 * <p>The path variable is constrained to the code charset so this mapping does not swallow asset
 * requests (e.g. {@code /favicon.ico}) or the {@code /api} and {@code /actuator} trees.
 */
@RestController
public class RedirectController {

  private final LinkService linkService;
  private final ClickAnalyticsService analytics;

  public RedirectController(LinkService linkService, ClickAnalyticsService analytics) {
    this.linkService = linkService;
    this.analytics = analytics;
  }

  @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
  public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
    String targetUrl = linkService.resolveTargetUrl(code);

    // Only successful redirects are clicks (decision A1). Capture is async and best-effort.
    analytics.recordAsync(
        new ClickContext(
            code,
            clientIp(request),
            request.getHeader("User-Agent"),
            request.getHeader("Referer")));

    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, targetUrl)
        .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, max-age=0")
        .build();
  }

  /**
   * Best-effort client IP: first hop of X-Forwarded-For when present, else the socket address.
   * Note: X-Forwarded-For is client-supplied and only trustworthy behind a controlled proxy; for
   * approximate analytics this is acceptable and the value is only ever stored as a salted hash.
   */
  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
    }
    return request.getRemoteAddr();
  }
}
