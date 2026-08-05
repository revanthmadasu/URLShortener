package com.example.urlshortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.support.TestFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickAnalyticsServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

  @Mock private ClickEventRepository repository;

  private ClickAnalyticsService service() {
    return new ClickAnalyticsService(
        repository, TestFixtures.appProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void recordsClickWithHashedIpNotRawIp() {
    ClickAnalyticsService service = service();

    service.recordAsync(
        new ClickContext("abc1234", "203.0.113.7", "curl/8", "https://ref.example"));

    ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
    verify(repository).save(captor.capture());
    ClickEvent saved = captor.getValue();
    assertThat(saved.getShortCode()).isEqualTo("abc1234");
    assertThat(saved.getIpHash()).isNotNull().doesNotContain("203.0.113.7").hasSize(64);
    assertThat(saved.getUserAgent()).isEqualTo("curl/8");
    assertThat(saved.getOccurredAt()).isEqualTo(NOW);
  }

  @Test
  void ipHashIsStableAndSaltDependent() {
    ClickAnalyticsService service = service();
    assertThat(service.hashIp("203.0.113.7")).isEqualTo(service.hashIp("203.0.113.7"));
    assertThat(service.hashIp("203.0.113.7")).isNotEqualTo(service.hashIp("203.0.113.8"));
    assertThat(service.hashIp(null)).isNull();
  }

  @Test
  void recordingNeverThrowsEvenIfPersistenceFails() {
    ClickAnalyticsService service = service();
    doThrow(new RuntimeException("db down")).when(repository).save(any());

    // Must swallow: a redirect already succeeded; analytics is best-effort.
    service.recordAsync(new ClickContext("abc1234", "203.0.113.7", null, null));
  }

  @Test
  void statsQueriesRepositoryOverWindow() {
    ClickAnalyticsService service = service();
    when(repository.countSince(eq("abc1234"), any())).thenReturn(42L);
    when(repository.countDistinctVisitorsSince(eq("abc1234"), any())).thenReturn(10L);
    when(repository.clicksByDay(eq("abc1234"), any())).thenReturn(java.util.List.of());
    when(repository.topReferrers(eq("abc1234"), any(), anyInt())).thenReturn(java.util.List.of());

    var stats = service.stats("abc1234", 30);

    assertThat(stats.totalClicks()).isEqualTo(42L);
    assertThat(stats.uniqueVisitors()).isEqualTo(10L);
    assertThat(stats.windowDays()).isEqualTo(30);
  }
}
