package com.example.urlshortener.analytics;

import com.example.urlshortener.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically deletes click events older than the configured retention window (data minimization,
 * risk R8). Runs daily; the deletion is a single bulk statement guarded by the composite index.
 */
@Component
public class ClickRetentionSweeper {

  private static final Logger log = LoggerFactory.getLogger(ClickRetentionSweeper.class);

  private final ClickEventRepository repository;
  private final AppProperties.Analytics config;
  private final Clock clock;

  public ClickRetentionSweeper(
      ClickEventRepository repository, AppProperties properties, Clock clock) {
    this.repository = repository;
    this.config = properties.analytics();
    this.clock = clock;
  }

  /** Runs daily at 03:15 UTC. Also invocable directly (tests, ops). */
  @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
  @Transactional
  public int sweep() {
    Instant cutoff = clock.instant().minus(config.retention());
    int deleted = repository.deleteOlderThan(cutoff);
    if (deleted > 0) {
      log.info("Retention sweep deleted {} click events older than {}", deleted, cutoff);
    }
    return deleted;
  }
}
