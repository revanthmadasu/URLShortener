package com.example.urlshortener.link.dto;

import com.example.urlshortener.link.Link;
import java.time.Instant;

/**
 * Public view of a link. Deliberately excludes the management token (returned only once, by
 * {@link CreateLinkResponse}) and internal id.
 */
public record LinkResponse(
    String shortCode, String shortUrl, String longUrl, Instant createdAt, Instant expiresAt) {

  public static LinkResponse from(Link link, String baseUrl) {
    return new LinkResponse(
        link.getShortCode(),
        baseUrl + "/" + link.getShortCode(),
        link.getLongUrl(),
        link.getCreatedAt(),
        link.getExpiresAt());
  }
}
