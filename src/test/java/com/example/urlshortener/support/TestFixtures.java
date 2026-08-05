package com.example.urlshortener.support;

import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.config.AppProperties.Cache;
import com.example.urlshortener.config.AppProperties.Code;
import com.example.urlshortener.config.AppProperties.Redirect;
import com.example.urlshortener.config.AppProperties.Security;
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
        new Code(codeLength, ALPHABET),
        new Cache(Duration.ofHours(1), Duration.ofSeconds(30)),
        new Security(requireManagementToken),
        new Redirect(List.of("http", "https"), true));
  }
}
