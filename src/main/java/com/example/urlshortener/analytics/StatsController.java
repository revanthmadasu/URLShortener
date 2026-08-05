package com.example.urlshortener.analytics;

import com.example.urlshortener.analytics.dto.ClickStatsResponse;
import com.example.urlshortener.link.LinkService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes click statistics for a link: {@code GET /api/v1/links/{code}/stats?days=N}. Returns 404
 * (via {@link LinkService#getByCode}) if the code does not exist, so stats and the link share a
 * consistent notion of existence.
 */
@RestController
@Validated
public class StatsController {

  private final LinkService linkService;
  private final ClickAnalyticsService analytics;

  public StatsController(LinkService linkService, ClickAnalyticsService analytics) {
    this.linkService = linkService;
    this.analytics = analytics;
  }

  @GetMapping("/api/v1/links/{code}/stats")
  public ClickStatsResponse stats(
      @PathVariable String code,
      @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
    linkService.getByCode(code); // 404 if unknown
    return analytics.stats(code, days);
  }
}
