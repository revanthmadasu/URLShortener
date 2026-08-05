package com.example.urlshortener.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.support.TestFixtures;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

  private static final class MutableClock extends java.time.Clock {
    private Instant now = Instant.parse("2026-08-05T00:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public java.time.Clock withZone(ZoneId zone) {
      return this;
    }
  }

  private AppProperties propsWith(int capacity, Duration refill) {
    AppProperties base = TestFixtures.appProperties();
    return new AppProperties(
        base.baseUrl(),
        base.code(),
        base.cache(),
        base.security(),
        base.redirect(),
        base.analytics(),
        new AppProperties.RateLimit(true, capacity, refill));
  }

  @Test
  void allowsUpToCapacityThenBlocks() {
    MutableClock clock = new MutableClock();
    TokenBucketRateLimiter limiter =
        new TokenBucketRateLimiter(propsWith(3, Duration.ofMinutes(1)), clock);

    assertThat(limiter.tryAcquire("ip")).isTrue();
    assertThat(limiter.tryAcquire("ip")).isTrue();
    assertThat(limiter.tryAcquire("ip")).isTrue();
    assertThat(limiter.tryAcquire("ip")).isFalse(); // bucket empty
  }

  @Test
  void refillsOverTime() {
    MutableClock clock = new MutableClock();
    TokenBucketRateLimiter limiter =
        new TokenBucketRateLimiter(propsWith(2, Duration.ofSeconds(2)), clock);

    assertThat(limiter.tryAcquire("ip")).isTrue();
    assertThat(limiter.tryAcquire("ip")).isTrue();
    assertThat(limiter.tryAcquire("ip")).isFalse();

    clock.advance(Duration.ofSeconds(1)); // refill rate = 1 token/sec -> +1 token
    assertThat(limiter.tryAcquire("ip")).isTrue();
    assertThat(limiter.tryAcquire("ip")).isFalse();
  }

  @Test
  void bucketsAreIndependentPerKey() {
    MutableClock clock = new MutableClock();
    TokenBucketRateLimiter limiter =
        new TokenBucketRateLimiter(propsWith(1, Duration.ofMinutes(1)), clock);

    assertThat(limiter.tryAcquire("a")).isTrue();
    assertThat(limiter.tryAcquire("a")).isFalse();
    assertThat(limiter.tryAcquire("b")).isTrue(); // different key, own bucket
  }
}
