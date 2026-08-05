package com.example.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.link.RedirectCache.Lookup;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedirectCacheTest {

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> ops;

  private RedirectCache cache;

  @BeforeEach
  void setUp() {
    lenient().when(redis.opsForValue()).thenReturn(ops);
    cache = new RedirectCache(redis, Clock.systemUTC());
  }

  @Test
  void returnsHitForStoredUrl() {
    when(ops.get("url:abc1234")).thenReturn("https://example.com");
    assertThat(cache.lookup("abc1234").kind()).isEqualTo(Lookup.Kind.HIT);
    assertThat(cache.lookup("abc1234").url()).isEqualTo("https://example.com");
  }

  @Test
  void returnsMissForAbsentKey() {
    when(ops.get("url:abc1234")).thenReturn(null);
    assertThat(cache.lookup("abc1234").kind()).isEqualTo(Lookup.Kind.MISS);
  }

  @Test
  void returnsNegativeForSentinel() {
    when(ops.get("url:abc1234")).thenReturn("__NEGATIVE__");
    assertThat(cache.lookup("abc1234").kind()).isEqualTo(Lookup.Kind.NEGATIVE);
  }

  @Test
  void degradesToMissWhenRedisThrows() {
    when(ops.get(anyString())).thenThrow(new RuntimeException("connection refused"));
    // Must not propagate; caller falls back to the database.
    assertThat(cache.lookup("abc1234").kind()).isEqualTo(Lookup.Kind.MISS);
  }

  @Test
  void breakerOpensAndStopsCallingRedisAfterRepeatedFailures() {
    when(ops.get(anyString())).thenThrow(new RuntimeException("down"));

    // Threshold is 5 consecutive failures. After that the breaker is open and lookups
    // short-circuit without touching Redis (within the 5s cooldown window this test runs in).
    for (int i = 0; i < 10; i++) {
      assertThat(cache.lookup("abc1234").kind()).isEqualTo(Lookup.Kind.MISS);
    }
    verify(ops, times(5)).get(anyString());
  }

  @Test
  void skipsWriteForNonPositiveTtl() {
    cache.putPositive("abc1234", "https://example.com", Duration.ZERO);
    verify(ops, times(0))
        .set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
  }
}
