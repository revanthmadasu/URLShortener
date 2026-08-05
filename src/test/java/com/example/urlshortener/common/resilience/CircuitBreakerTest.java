package com.example.urlshortener.common.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

  /** A hand-advanceable clock so cooldown behavior is tested without sleeping. */
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
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public java.time.Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  }

  @Test
  void staysClosedBelowThreshold() {
    CircuitBreaker cb = new CircuitBreaker(3, Duration.ofSeconds(5), new MutableClock());
    cb.recordFailure();
    cb.recordFailure();
    assertThat(cb.allowRequest()).isTrue();
    assertThat(cb.isOpen()).isFalse();
  }

  @Test
  void opensAtThresholdAndBlocks() {
    CircuitBreaker cb = new CircuitBreaker(3, Duration.ofSeconds(5), new MutableClock());
    cb.recordFailure();
    cb.recordFailure();
    cb.recordFailure();
    assertThat(cb.isOpen()).isTrue();
    assertThat(cb.allowRequest()).isFalse();
  }

  @Test
  void successResetsFailureCount() {
    CircuitBreaker cb = new CircuitBreaker(3, Duration.ofSeconds(5), new MutableClock());
    cb.recordFailure();
    cb.recordFailure();
    cb.recordSuccess();
    cb.recordFailure();
    cb.recordFailure();
    assertThat(cb.isOpen()).isFalse(); // only 2 consecutive after reset
  }

  @Test
  void halfOpensAfterCooldownThenClosesOnSuccess() {
    MutableClock clock = new MutableClock();
    CircuitBreaker cb = new CircuitBreaker(1, Duration.ofSeconds(5), clock);
    cb.recordFailure(); // opens immediately (threshold 1)
    assertThat(cb.allowRequest()).isFalse();

    clock.advance(Duration.ofSeconds(6));
    assertThat(cb.allowRequest()).isTrue(); // half-open trial
    cb.recordSuccess();
    assertThat(cb.isOpen()).isFalse();
  }

  @Test
  void halfOpenFailureReopens() {
    MutableClock clock = new MutableClock();
    CircuitBreaker cb = new CircuitBreaker(1, Duration.ofSeconds(5), clock);
    cb.recordFailure();
    clock.advance(Duration.ofSeconds(6));
    assertThat(cb.allowRequest()).isTrue(); // half-open
    cb.recordFailure(); // trial failed
    assertThat(cb.allowRequest()).isFalse(); // reopened
  }
}
