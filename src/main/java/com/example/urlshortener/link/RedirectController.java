package com.example.urlshortener.link;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the public redirect: {@code GET /{code}} → 302 to the destination.
 *
 * <p><b>Why 302 (Found), not 301 (Moved Permanently).</b> A 301 is aggressively cached by
 * browsers and intermediaries, which would prevent the service from observing subsequent
 * clicks — fatal for the analytics feature (Phase 3). A 302 keeps the code reaching the
 * server on every hit. We also mark the response non-cacheable for the same reason. The
 * trade-off is a small latency cost per redirect versus accurate click accounting; the
 * hot-path Redis cache (Phase 2) keeps that cost low.
 *
 * <p>The path variable is constrained to the code charset so this mapping does not swallow
 * asset requests (e.g. {@code /favicon.ico}) or the {@code /api} and {@code /actuator} trees.
 */
@RestController
public class RedirectController {

  private final LinkService linkService;

  public RedirectController(LinkService linkService) {
    this.linkService = linkService;
  }

  @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
  public ResponseEntity<Void> redirect(@PathVariable String code) {
    Link link = linkService.resolveForRedirect(code);
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, link.getLongUrl())
        .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, max-age=0")
        .build();
  }
}
