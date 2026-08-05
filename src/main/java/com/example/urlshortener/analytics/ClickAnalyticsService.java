package com.example.urlshortener.analytics;

import com.example.urlshortener.analytics.dto.ClickStatsResponse;
import com.example.urlshortener.analytics.dto.ClickStatsResponse.DailyClicks;
import com.example.urlshortener.analytics.dto.ClickStatsResponse.ReferrerStat;
import com.example.urlshortener.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Records clicks asynchronously and computes aggregated statistics.
 *
 * <p>Capture is fire-and-forget on the {@code clickExecutor}: a failure to record a click must
 * never affect the redirect (see decision A8). The client IP is stored only as an HMAC (decision
 * A3); the raw IP is never persisted.
 */
@Service
public class ClickAnalyticsService {

  private static final Logger log = LoggerFactory.getLogger(ClickAnalyticsService.class);
  private static final int MAX_UA = 512;
  private static final int MAX_REFERER = 2048;
  private static final int TOP_REFERRERS = 5;

  private final ClickEventRepository repository;
  private final AppProperties.Analytics config;
  private final Clock clock;
  private final byte[] salt;

  public ClickAnalyticsService(
      ClickEventRepository repository, AppProperties properties, Clock clock) {
    this.repository = repository;
    this.config = properties.analytics();
    this.clock = clock;
    this.salt = config.ipSalt().getBytes(StandardCharsets.UTF_8);
  }

  /** Record a click off the request thread. Never throws to the caller. */
  @Async("clickExecutor")
  public void recordAsync(ClickContext context) {
    if (!config.enabled()) {
      return;
    }
    try {
      ClickEvent event =
          ClickEvent.of(
              context.shortCode(),
              clock.instant(),
              hashIp(context.clientIp()),
              truncate(context.userAgent(), MAX_UA),
              truncate(context.referer(), MAX_REFERER));
      repository.save(event);
    } catch (RuntimeException e) {
      // Analytics is best-effort; a redirect already succeeded. Log and move on.
      log.warn("Failed to record click for code '{}': {}", context.shortCode(), e.toString());
    }
  }

  /** Aggregate stats for a link over the last {@code days} days (UTC). */
  public ClickStatsResponse stats(String shortCode, int days) {
    Instant since = clock.instant().minus(Duration.ofDays(days));

    long total = repository.countSince(shortCode, since);
    long unique = repository.countDistinctVisitorsSince(shortCode, since);
    List<DailyClicks> byDay =
        repository.clicksByDay(shortCode, since).stream()
            .map(d -> new DailyClicks(d.getDay(), d.getCount()))
            .toList();
    List<ReferrerStat> referrers =
        repository.topReferrers(shortCode, since, TOP_REFERRERS).stream()
            .map(r -> new ReferrerStat(r.getReferer(), r.getCount()))
            .toList();

    return new ClickStatsResponse(shortCode, days, total, unique, byDay, referrers);
  }

  /** HMAC-SHA256 of the IP, hex, first 64 chars. Returns null when no IP is available. */
  String hashIp(String ip) {
    if (ip == null || ip.isBlank()) {
      return null;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(salt, "HmacSHA256"));
      byte[] out = mac.doFinal(ip.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(out);
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
