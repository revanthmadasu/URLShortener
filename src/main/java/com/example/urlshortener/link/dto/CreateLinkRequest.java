package com.example.urlshortener.link.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Request to create a short link.
 *
 * @param url the destination URL. Required; must be a syntactically valid absolute URL. The scheme
 *     allowlist and SSRF/private-network checks are enforced in the service layer (see {@code
 *     UrlValidator}) because they are policy, not mere syntax.
 * @param customAlias optional caller-chosen code. Restricted to a URL-safe charset and a sane
 *     length. If absent, a code is generated.
 * @param expiresAt optional expiry instant (UTC). If absent, the link never expires. Must be in the
 *     future — validated in the service layer against the request-time clock.
 */
public record CreateLinkRequest(
    @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url,
    @Pattern(
            regexp = "^[A-Za-z0-9_-]{3,32}$",
            message = "customAlias must be 3-32 chars of [A-Za-z0-9_-]")
        String customAlias,
    Instant expiresAt) {}
