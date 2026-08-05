package com.example.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.common.error.Errors;
import com.example.urlshortener.support.TestFixtures;
import java.net.InetAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

  private final UrlValidator validator = TestFixtures.urlValidator();

  @Test
  @DisplayName("accepts and trims a valid https URL")
  void acceptsValidHttps() {
    assertThat(validator.validate("  https://example.com/path?q=1  "))
        .isEqualTo("https://example.com/path?q=1");
  }

  @Test
  void acceptsValidHttp() {
    assertThat(validator.validate("http://example.com")).isEqualTo("http://example.com");
  }

  @ParameterizedTest(name = "rejects dangerous/invalid scheme: {0}")
  @ValueSource(
      strings = {
        "javascript:alert(1)",
        "data:text/html,<script>alert(1)</script>",
        "file:///etc/passwd",
        "ftp://example.com/x"
      })
  void rejectsDisallowedSchemes(String url) {
    assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(Errors.InvalidUrl.class);
  }

  @ParameterizedTest(name = "rejects structurally invalid URL: {0}")
  @ValueSource(strings = {"not a url", "example.com", "/relative/path", "https://"})
  void rejectsStructurallyInvalid(String url) {
    assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(Errors.InvalidUrl.class);
  }

  @Nested
  class PrivateNetworkBlocking {

    /** Validator whose guard resolves any host to the supplied literal address(es). */
    private UrlValidator validatorResolvingTo(String... literalIps) {
      PrivateNetworkGuard guard =
          new PrivateNetworkGuard(
              host -> {
                InetAddress[] addrs = new InetAddress[literalIps.length];
                for (int i = 0; i < literalIps.length; i++) {
                  addrs[i] = InetAddress.getByName(literalIps[i]); // literal → no DNS
                }
                return addrs;
              });
      return new UrlValidator(TestFixtures.appProperties(), guard);
    }

    @ParameterizedTest(name = "blocks host resolving to {0}")
    @ValueSource(
        strings = {
          "127.0.0.1", // loopback
          "10.0.0.1", // private
          "172.16.5.4", // private
          "192.168.1.1", // private
          "169.254.169.254", // link-local / cloud metadata
          "0.0.0.0", // wildcard
          "::1", // IPv6 loopback
          "fc00::1", // IPv6 unique-local
          "fd12:3456::1", // IPv6 unique-local
          "224.0.0.1" // multicast
        })
    void blocksPrivateAndInternalTargets(String ip) {
      UrlValidator v = validatorResolvingTo(ip);
      assertThatThrownBy(() -> v.validate("https://internal.example/x"))
          .isInstanceOf(Errors.InvalidUrl.class);
    }

    @Test
    void allowsPublicTarget() {
      UrlValidator v = validatorResolvingTo("93.184.216.34");
      assertThat(v.validate("https://example.com")).isEqualTo("https://example.com");
    }

    @Test
    void blocksWhenAnyResolvedAddressIsPrivate() {
      // DNS returning both a public and a private address must still be rejected.
      UrlValidator v = validatorResolvingTo("93.184.216.34", "10.1.2.3");
      assertThatThrownBy(() -> v.validate("https://sneaky.example"))
          .isInstanceOf(Errors.InvalidUrl.class);
    }

    @Test
    void failsClosedOnUnresolvableHost() {
      PrivateNetworkGuard guard =
          new PrivateNetworkGuard(
              host -> {
                throw new java.net.UnknownHostException(host);
              });
      UrlValidator v = new UrlValidator(TestFixtures.appProperties(), guard);
      assertThatThrownBy(() -> v.validate("https://does-not-resolve.invalid"))
          .isInstanceOf(Errors.InvalidUrl.class);
    }
  }
}
