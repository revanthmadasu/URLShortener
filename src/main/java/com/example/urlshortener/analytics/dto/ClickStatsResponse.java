package com.example.urlshortener.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Aggregated click statistics for a link over a window of {@code windowDays} days (UTC).
 *
 * @param shortCode the link's code
 * @param windowDays size of the window the figures cover
 * @param totalClicks successful redirects in the window
 * @param uniqueVisitors distinct salted IP hashes in the window (an estimate; see docs)
 * @param clicksByDay per-UTC-day counts, ascending
 * @param topReferrers most frequent referrers in the window
 */
public record ClickStatsResponse(
    String shortCode,
    int windowDays,
    long totalClicks,
    long uniqueVisitors,
    List<DailyClicks> clicksByDay,
    List<ReferrerStat> topReferrers) {

  public record DailyClicks(LocalDate day, long clicks) {}

  public record ReferrerStat(String referer, long clicks) {}
}
