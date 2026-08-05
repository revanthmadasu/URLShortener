package com.example.urlshortener.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Strongly-typed application configuration bound from the {@code app.*} namespace.
 *
 * <p>Using an immutable record keeps configuration explicit and testable: every knob the
 * service exposes lives here with a default, rather than being scattered across
 * {@code @Value} injections.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @DefaultValue("http://localhost:8080") String baseUrl,
    @DefaultValue Code code,
    @DefaultValue Cache cache,
    @DefaultValue Security security,
    @DefaultValue Redirect redirect) {

  public record Code(
      @DefaultValue("7") int length,
      @DefaultValue("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
          String alphabet,
      @DefaultValue("feistel") Strategy strategy,
      // Key that parameterizes the Feistel permutation. Codes are not secret, so this is about
      // making output non-sequential, not cryptographic secrecy. Override per environment to
      // change the code ordering. Changing it after codes exist does not affect stored codes.
      @DefaultValue("2654435769") long feistelKey) {

    public enum Strategy {
      FEISTEL,
      RANDOM
    }
  }

  public record Cache(
      @DefaultValue("1h") Duration ttl, @DefaultValue("30s") Duration negativeTtl) {}

  public record Security(@DefaultValue("true") boolean requireManagementToken) {}

  public record Redirect(
      @DefaultValue({"http", "https"}) List<String> allowedSchemes,
      @DefaultValue("true") boolean blockPrivateNetworks) {}
}
