package com.example.urlshortener.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Verifies the rate-limit interceptor returns 429 once the per-IP bucket is exhausted. Capacity is
 * lowered to 3 with a long refill so the fourth create in a burst is rejected. Requires Docker.
 */
@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.rate-limit.capacity=3", "app.rate-limit.refill-period=1h"})
class RateLimitIT {

  @LocalServerPort private int port;

  @Test
  void returns429AfterCapacityExceeded() {
    RestClient client = RestClient.builder().baseUrl("http://localhost:" + port).build();

    for (int i = 0; i < 3; i++) {
      HttpStatus status = create(client);
      assertThat(status).isEqualTo(HttpStatus.CREATED);
    }
    // Fourth request in the burst exceeds the bucket.
    assertThat(create(client)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  private HttpStatus create(RestClient client) {
    return (HttpStatus)
        client
            .post()
            .uri("/api/v1/links")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"url\":\"https://example.com\"}")
            .retrieve()
            .onStatus(s -> true, (req, res) -> {})
            .toBodilessEntity()
            .getStatusCode();
  }
}
