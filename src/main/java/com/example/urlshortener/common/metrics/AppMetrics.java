package com.example.urlshortener.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Thin facade over Micrometer for the service's domain metrics, so business code depends on a
 * small, intention-revealing API rather than the metrics library directly. Exposed on {@code
 * /actuator/prometheus} alongside the built-in JVM/HTTP metrics.
 */
@Component
public class AppMetrics {

  private final Counter linksCreated;
  private final Counter cacheHit;
  private final Counter cacheMiss;
  private final Counter cacheNegative;
  private final Timer redirectLatency;

  public AppMetrics(MeterRegistry registry) {
    this.linksCreated = Counter.builder("urlshortener.links.created").register(registry);
    this.cacheHit =
        Counter.builder("urlshortener.redirect.cache").tag("result", "hit").register(registry);
    this.cacheMiss =
        Counter.builder("urlshortener.redirect.cache").tag("result", "miss").register(registry);
    this.cacheNegative =
        Counter.builder("urlshortener.redirect.cache").tag("result", "negative").register(registry);
    // Dedicated timer for the redirect resolution itself — a clean SLI that counts only real
    // redirects (unlike http.server.requests, which also counts health checks and metrics polls).
    this.redirectLatency =
        Timer.builder("urlshortener.redirect.latency")
            .description("Time to resolve a short code to its destination URL")
            .register(registry);
  }

  public void linkCreated() {
    linksCreated.increment();
  }

  public void cacheHit() {
    cacheHit.increment();
  }

  public void cacheMiss() {
    cacheMiss.increment();
  }

  public void cacheNegative() {
    cacheNegative.increment();
  }

  /** Record the wall-clock time taken to resolve a redirect. */
  public void recordRedirectLatency(Duration duration) {
    redirectLatency.record(duration);
  }
}
