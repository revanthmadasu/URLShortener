package com.example.urlshortener.link;

import com.example.urlshortener.common.error.Errors;
import com.example.urlshortener.config.AppProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Validates destination URLs before they are stored.
 *
 * <p>Checks, in order: syntactic validity, an absolute URL, a present host, a scheme on the
 * allowlist (http/https — blocks {@code javascript:}, {@code data:}, {@code file:}, …), and, when
 * {@code app.redirect.block-private-networks} is enabled, that the host does not resolve to a
 * private/loopback/link-local/metadata range (see {@link PrivateNetworkGuard}, risk R3).
 */
@Component
public class UrlValidator {

  private final List<String> allowedSchemes;
  private final boolean blockPrivateNetworks;
  private final PrivateNetworkGuard privateNetworkGuard;

  public UrlValidator(AppProperties properties, PrivateNetworkGuard privateNetworkGuard) {
    this.allowedSchemes =
        properties.redirect().allowedSchemes().stream()
            .map(s -> s.toLowerCase(Locale.ROOT))
            .toList();
    this.blockPrivateNetworks = properties.redirect().blockPrivateNetworks();
    this.privateNetworkGuard = privateNetworkGuard;
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
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new Errors.InvalidUrl("URL must include a host");
    }
    if (blockPrivateNetworks && privateNetworkGuard.isDisallowed(host)) {
      throw new Errors.InvalidUrl("URL host resolves to a disallowed (private/internal) address");
    }
    return uri.toString();
  }
}
