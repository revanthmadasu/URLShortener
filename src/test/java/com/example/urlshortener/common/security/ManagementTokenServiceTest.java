package com.example.urlshortener.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.common.security.ManagementTokenService.IssuedToken;
import org.junit.jupiter.api.Test;

class ManagementTokenServiceTest {

  private final ManagementTokenService service = new ManagementTokenService();

  @Test
  void issuesTokenWhoseHashMatches() {
    IssuedToken issued = service.issue();

    assertThat(issued.token()).isNotBlank();
    assertThat(issued.hash()).hasSize(64); // SHA-256 hex
    assertThat(service.matches(issued.token(), issued.hash())).isTrue();
  }

  @Test
  void doesNotStorePlaintext() {
    IssuedToken issued = service.issue();
    assertThat(issued.hash()).isNotEqualTo(issued.token());
  }

  @Test
  void rejectsWrongToken() {
    IssuedToken issued = service.issue();
    assertThat(service.matches("wrong-token", issued.hash())).isFalse();
  }

  @Test
  void rejectsNulls() {
    IssuedToken issued = service.issue();
    assertThat(service.matches(null, issued.hash())).isFalse();
    assertThat(service.matches(issued.token(), null)).isFalse();
  }

  @Test
  void hashIsDeterministic() {
    assertThat(service.hash("abc")).isEqualTo(service.hash("abc"));
  }
}
