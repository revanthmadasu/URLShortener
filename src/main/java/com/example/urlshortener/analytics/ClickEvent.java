package com.example.urlshortener.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One recorded click on a short link. See {@code V3__create_click_events.sql}. */
@Entity
@Table(name = "click_events")
public class ClickEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "short_code", nullable = false, length = 32, updatable = false)
  private String shortCode;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  /** HMAC-SHA256(ip, salt); never the raw IP. Nullable when the IP is unavailable. */
  @Column(name = "ip_hash", length = 64, updatable = false)
  private String ipHash;

  @Column(name = "user_agent", length = 512, updatable = false)
  private String userAgent;

  @Column(name = "referer", length = 2048, updatable = false)
  private String referer;

  protected ClickEvent() {}

  private ClickEvent(
      String shortCode, Instant occurredAt, String ipHash, String userAgent, String referer) {
    this.shortCode = shortCode;
    this.occurredAt = occurredAt;
    this.ipHash = ipHash;
    this.userAgent = userAgent;
    this.referer = referer;
  }

  public static ClickEvent of(
      String shortCode, Instant occurredAt, String ipHash, String userAgent, String referer) {
    return new ClickEvent(shortCode, occurredAt, ipHash, userAgent, referer);
  }

  public Long getId() {
    return id;
  }

  public String getShortCode() {
    return shortCode;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getIpHash() {
    return ipHash;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getReferer() {
    return referer;
  }
}
