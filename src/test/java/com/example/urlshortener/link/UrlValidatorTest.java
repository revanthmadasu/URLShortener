package com.example.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.common.error.Errors;
import com.example.urlshortener.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

  private final UrlValidator validator = new UrlValidator(TestFixtures.appProperties());

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
}
