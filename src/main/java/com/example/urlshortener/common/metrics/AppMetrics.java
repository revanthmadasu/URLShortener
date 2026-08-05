package com.example.urlshortener.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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

  public AppMetrics(MeterRegistry registry) {
    this.linksCreated = Counter.builder("urlshortener.links.created").register(registry);
    this.cacheHit =
        Counter.builder("urlshortener.redirect.cache").tag("result", "hit").register(registry);
    this.cacheMiss =
        Counter.builder("urlshortener.redirect.cache").tag("result", "miss").register(registry);
    this.cacheNegative =
        Counter.builder("urlshortener.redirect.cache").tag("result", "negative").register(registry);
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
}
