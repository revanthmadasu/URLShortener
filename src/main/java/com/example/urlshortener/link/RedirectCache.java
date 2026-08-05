package com.example.urlshortener.link;

import com.example.urlshortener.common.resilience.CircuitBreaker;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed cache for the redirect hot path (code → destination URL), with a negative cache for
 * unknown codes.
 *
 * <p><b>Redis is optional.</b> Every operation is wrapped so that any Redis failure degrades to a
 * cache miss (the caller then uses Postgres) rather than surfacing an error. A {@link
 * CircuitBreaker} ensures that once Redis is failing, we stop attempting it (and paying its
 * timeout) on every request until a cooldown elapses. This is why Redis is excluded from the
 * health/liveness check — its outage slows redirects, it does not break them.
 */
@Component
public class RedirectCache {

  private static final Logger log = LoggerFactory.getLogger(RedirectCache.class);

  /**
   * Sentinel stored for known-absent codes. Cannot collide with a real cached value because every
   * cached URL is validated to carry an http/https scheme (contains "://").
   */
  private static final String NEGATIVE = "__NEGATIVE__";

  private static final String KEY_PREFIX = "url:";

  /** Result of a cache lookup: a hit with the URL, a known-negative, or a miss. */
  public record Lookup(Kind kind, String url) {
    public enum Kind {
      HIT,
      NEGATIVE,
      MISS
    }

    public static Lookup hit(String url) {
      return new Lookup(Kind.HIT, url);
    }

    public static Lookup negative() {
      return NEGATIVE_RESULT;
    }

    public static Lookup miss() {
      return MISS_RESULT;
    }

    private static final Lookup NEGATIVE_RESULT = new Lookup(Kind.NEGATIVE, null);
    private static final Lookup MISS_RESULT = new Lookup(Kind.MISS, null);
  }

  private final StringRedisTemplate redis;
  private final CircuitBreaker breaker;

  public RedirectCache(StringRedisTemplate redis, Clock clock) {
    this.redis = redis;
    // Open after 5 consecutive failures; retry after 5s. Tuned for a cache, not a primary store.
    this.breaker = new CircuitBreaker(5, Duration.ofSeconds(5), clock);
  }

  public Lookup lookup(String code) {
    if (!breaker.allowRequest()) {
      return Lookup.miss(); // breaker open: skip Redis entirely
    }
    try {
      String value = redis.opsForValue().get(KEY_PREFIX + code);
      breaker.recordSuccess();
      if (value == null) {
        return Lookup.miss();
      }
      return NEGATIVE.equals(value) ? Lookup.negative() : Lookup.hit(value);
    } catch (RuntimeException e) {
      degrade("lookup", code, e);
      return Lookup.miss();
    }
  }

  public void putPositive(String code, String url, Duration ttl) {
    if (ttl.isZero() || ttl.isNegative() || !breaker.allowRequest()) {
      return;
    }
    try {
      redis.opsForValue().set(KEY_PREFIX + code, url, ttl);
      breaker.recordSuccess();
    } catch (RuntimeException e) {
      degrade("putPositive", code, e);
    }
  }

  public void putNegative(String code, Duration ttl) {
    if (ttl.isZero() || ttl.isNegative() || !breaker.allowRequest()) {
      return;
    }
    try {
      redis.opsForValue().set(KEY_PREFIX + code, NEGATIVE, ttl);
      breaker.recordSuccess();
    } catch (RuntimeException e) {
      degrade("putNegative", code, e);
    }
  }

  /** Remove a cached entry (used on delete). Best-effort; failures are swallowed. */
  public void evict(String code) {
    if (!breaker.allowRequest()) {
      return;
    }
    try {
      redis.delete(KEY_PREFIX + code);
      breaker.recordSuccess();
    } catch (RuntimeException e) {
      degrade("evict", code, e);
    }
  }

  private void degrade(String op, String code, RuntimeException e) {
    breaker.recordFailure();
    // Debug, not error: cache failures are expected-and-handled, not incidents.
    log.debug(
        "Redis {} failed for code '{}', degrading to source of truth: {}", op, code, e.toString());
  }
}
