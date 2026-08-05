package com.example.urlshortener.link.dto;

import com.example.urlshortener.link.Link;

/**
 * Response returned once, at creation time. Includes the {@code managementToken} — the
 * capability required to later delete the link. It is shown exactly once and never
 * retrievable again (only its hash is stored), so clients must persist it if they need it.
 */
public record CreateLinkResponse(LinkResponse link, String managementToken) {

  public static CreateLinkResponse of(Link link, String baseUrl, String managementToken) {
    return new CreateLinkResponse(LinkResponse.from(link, baseUrl), managementToken);
  }
}
