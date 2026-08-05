package com.example.urlshortener.link;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A short-code → long-URL mapping. Persistence is owned by Flyway (see {@code
 * db/migration/V1__create_links.sql}); this entity maps to that schema and Hibernate only validates
 * it against the live schema ({@code ddl-auto=validate}).
 */
@Entity
@Table(name = "links")
public class Link {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "short_code", nullable = false, length = 32, updatable = false)
  private String shortCode;

  @Column(name = "long_url", nullable = false, columnDefinition = "text")
  private String longUrl;

  @Column(name = "management_token_hash", nullable = false, length = 64, updatable = false)
  private String managementTokenHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  protected Link() {
    // Required by JPA.
  }

  private Link(
      String shortCode,
      String longUrl,
      String managementTokenHash,
      Instant createdAt,
      Instant expiresAt) {
    this.shortCode = shortCode;
    this.longUrl = longUrl;
    this.managementTokenHash = managementTokenHash;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  /** Factory for a new mapping. {@code expiresAt} may be null (never expires). */
  public static Link create(
      String shortCode,
      String longUrl,
      String managementTokenHash,
      Instant createdAt,
      Instant expiresAt) {
    return new Link(shortCode, longUrl, managementTokenHash, createdAt, expiresAt);
  }

  public boolean isExpired(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }

  public Long getId() {
    return id;
  }

  public String getShortCode() {
    return shortCode;
  }

  public String getLongUrl() {
    return longUrl;
  }

  public String getManagementTokenHash() {
    return managementTokenHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
