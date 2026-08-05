package com.example.urlshortener.support;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.config.AppProperties.Cache;
import com.example.urlshortener.config.AppProperties.Code;
import com.example.urlshortener.config.AppProperties.Redirect;
import com.example.urlshortener.config.AppProperties.Security;
import com.example.urlshortener.link.PrivateNetworkGuard;
import com.example.urlshortener.link.UrlValidator;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

/** Shared test builders so individual tests stay focused on behavior, not wiring. */
public final class TestFixtures {

  public static final String ALPHABET =
      "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

  private TestFixtures() {}

  /** Default properties matching application.yml, with knobs overridable per test. */
  public static AppProperties appProperties() {
    return appProperties(7, true);
  }

  public static AppProperties appProperties(int codeLength, boolean requireManagementToken) {
    return new AppProperties(
        "http://localhost:8080",
        new Code(codeLength, ALPHABET, Code.Strategy.FEISTEL, 2654435769L),
        new Cache(Duration.ofHours(1), Duration.ofSeconds(30)),
        new Security(requireManagementToken),
        new Redirect(List.of("http", "https"), true),
        new AppProperties.Analytics(true, "test-salt", Duration.ofDays(90)));
  }

  /** A guard whose resolver always returns a public IP, so no host is ever blocked (no DNS). */
  public static PrivateNetworkGuard permissiveGuard() {
    return new PrivateNetworkGuard(host -> new InetAddress[] {publicAddress()});
  }

  /** A validator wired with the permissive guard — for tests unrelated to SSRF blocking. */
  public static UrlValidator urlValidator() {
    return new UrlValidator(appProperties(), permissiveGuard());
  }

  private static InetAddress publicAddress() {
    try {
      return InetAddress.getByName("93.184.216.34"); // literal, no DNS lookup
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }
}
