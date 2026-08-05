package com.example.urlshortener.common.resilience;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal thread-safe circuit breaker used to protect the redirect hot path from a slow or dead
 * cache. After {@code failureThreshold} consecutive failures the breaker <b>opens</b> and
 * {@link #allowRequest()} returns false for {@code openDuration}, so callers skip the failing
 * dependency (here: Redis) and go straight to the source of truth instead of paying a timeout on
 * every request. After the cooldown it moves to <b>half-open</b> and allows one trial; a success
 * closes it, a failure re-opens it.
 *
 * <p>Deliberately dependency-free (no Resilience4j): the behavior needed here is small, and a
 * self-contained implementation is easy to unit-test deterministically with an injected clock.
 */
public final class CircuitBreaker {

  private enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private final int failureThreshold;
  private final long openDurationMillis;
  private final java.time.Clock clock;

  private volatile State state = State.CLOSED;
  private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
  private final AtomicLong openedAtMillis = new AtomicLong(0);

  public CircuitBreaker(int failureThreshold, Duration openDuration, java.time.Clock clock) {
    this.failureThreshold = failureThreshold;
    this.openDurationMillis = openDuration.toMillis();
    this.clock = clock;
  }

  /** @return true if the caller should attempt the protected operation. */
  public boolean allowRequest() {
    if (state == State.OPEN) {
      if (clock.millis() - openedAtMillis.get() >= openDurationMillis) {
        state = State.HALF_OPEN; // allow a single trial request through
        return true;
      }
      return false;
    }
    return true; // CLOSED or HALF_OPEN
  }

  public void recordSuccess() {
    consecutiveFailures.set(0);
    state = State.CLOSED;
  }

  public void recordFailure() {
    if (state == State.HALF_OPEN) {
      open();
      return;
    }
    if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
      open();
    }
  }

  private void open() {
    state = State.OPEN;
    openedAtMillis.set(clock.millis());
  }

  public boolean isOpen() {
    return state == State.OPEN;
  }
}
