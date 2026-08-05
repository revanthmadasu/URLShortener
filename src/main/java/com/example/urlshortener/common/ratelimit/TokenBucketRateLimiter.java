package com.example.urlshortener.common.ratelimit;

import com.example.urlshortener.config.AppProperties;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory per-key token-bucket rate limiter.
 *
 * <p>Each key (client IP) gets a bucket that refills continuously at {@code capacity/refillPeriod}
 * and caps at {@code capacity}. {@link #tryAcquire} consumes one token if available.
 *
 * <p><b>Scope & limitation (documented, risk R2).</b> State is per-instance and in-memory, so
 * across N instances the effective limit is N× the configured value, and it resets on restart. That
 * is acceptable for this prototype; a distributed deployment would back this with Redis (e.g. a Lua
 * token-bucket) — the interface here is deliberately small enough to swap.
 */
@Component
public class TokenBucketRateLimiter {

  private final int capacity;
  private final double refillTokensPerMilli;
  private final Clock clock;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public TokenBucketRateLimiter(AppProperties properties, Clock clock) {
    AppProperties.RateLimit cfg = properties.rateLimit();
    this.capacity = cfg.capacity();
    long periodMillis = Math.max(1, cfg.refillPeriod().toMillis());
    this.refillTokensPerMilli = (double) cfg.capacity() / periodMillis;
    this.clock = clock;
  }

  /**
   * @return true if a token was available and consumed; false if the caller is rate-limited.
   */
  public boolean tryAcquire(String key) {
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, clock.millis()));
    return bucket.tryConsume(clock.millis(), capacity, refillTokensPerMilli);
  }

  /** Number of active buckets — exposed for a gauge metric. */
  public int size() {
    return buckets.size();
  }

  private static final class Bucket {
    private double tokens;
    private long lastRefillMillis;

    Bucket(double tokens, long nowMillis) {
      this.tokens = tokens;
      this.lastRefillMillis = nowMillis;
    }

    synchronized boolean tryConsume(long nowMillis, int capacity, double refillPerMilli) {
      long elapsed = nowMillis - lastRefillMillis;
      if (elapsed > 0) {
        tokens = Math.min(capacity, tokens + elapsed * refillPerMilli);
        lastRefillMillis = nowMillis;
      }
      if (tokens >= 1.0) {
        tokens -= 1.0;
        return true;
      }
      return false;
    }
  }
}
