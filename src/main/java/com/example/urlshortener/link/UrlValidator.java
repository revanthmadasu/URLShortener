package com.example.urlshortener.link;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.common.error.Errors;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Validates destination URLs before they are stored.
 *
 * <p>Phase 1 scope: syntactic validity, an absolute URL, a present host, and a scheme on the
 * allowlist (http/https). This blocks {@code javascript:}, {@code data:}, {@code file:} and
 * other dangerous schemes that would otherwise enable stored-XSS-style open redirects.
 *
 * <p><b>Known gap (closed in Phase 2 / brownfield).</b> This does not yet resolve the host to
 * check for private/loopback/link-local addresses, so it does not fully prevent SSRF via a
 * shortened link pointing at internal infrastructure. Tracked as risk R3.
 */
@Component
public class UrlValidator {

  private final List<String> allowedSchemes;

  public UrlValidator(AppProperties properties) {
    this.allowedSchemes =
        properties.redirect().allowedSchemes().stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
  }

  /** Returns the normalized URL string, or throws {@link Errors.InvalidUrl}. */
  public String validate(String rawUrl) {
    final URI uri;
    try {
      uri = new URI(rawUrl.trim());
    } catch (URISyntaxException e) {
      throw new Errors.InvalidUrl("URL is not a valid URI");
    }
    if (!uri.isAbsolute() || uri.getScheme() == null) {
      throw new Errors.InvalidUrl("URL must be absolute and include a scheme");
    }
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    if (!allowedSchemes.contains(scheme)) {
      throw new Errors.InvalidUrl("URL scheme '" + scheme + "' is not allowed");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new Errors.InvalidUrl("URL must include a host");
    }
    return uri.toString();
  }
}
