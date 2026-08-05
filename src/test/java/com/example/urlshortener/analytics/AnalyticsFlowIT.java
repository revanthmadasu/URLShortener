package com.example.urlshortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.urlshortener.TestcontainersConfiguration;
import com.example.urlshortener.analytics.dto.ClickStatsResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * End-to-end analytics: create a link, drive several redirects with varied IPs/referers, wait for
 * the async capture to land, then assert the aggregated stats. Requires Docker.
 */
@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalyticsFlowIT {

  @LocalServerPort private int port;
  @Autowired private ClickEventRepository clickEventRepository;

  private RestClient client() {
    return RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @BeforeEach
  @AfterEach
  void clearEvents() {
    // Isolate from click events any other IT produced (redirects elsewhere also record clicks).
    clickEventRepository.deleteAll();
  }

  @Test
  void aggregatesClicksUniquesAndReferrers() {
    String code =
        client()
            .post()
            .uri("/api/v1/links")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReq("https://example.com/target", null, null))
            .retrieve()
            .body(CreateBody.class)
            .link()
            .shortCode();

    // 3 clicks from 2 distinct IPs; 2 referers.
    hit(code, "1.1.1.1", "https://a.example");
    hit(code, "1.1.1.1", "https://a.example");
    hit(code, "2.2.2.2", "https://b.example");

    // Capture is async; wait until all 3 events are persisted.
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> clickEventRepository.count() == 3L);

    ClickStatsResponse stats =
        client()
            .get()
            .uri("/api/v1/links/" + code + "/stats?days=30")
            .retrieve()
            .body(ClickStatsResponse.class);

    assertThat(stats.totalClicks()).isEqualTo(3L);
    assertThat(stats.uniqueVisitors()).isEqualTo(2L); // two distinct IP hashes
    assertThat(stats.clicksByDay()).isNotEmpty();
    assertThat(stats.topReferrers())
        .extracting(ClickStatsResponse.ReferrerStat::referer)
        .contains("https://a.example", "https://b.example");
    // Most frequent referrer first.
    assertThat(stats.topReferrers().get(0).referer()).isEqualTo("https://a.example");
  }

  private void hit(String code, String ip, String referer) {
    client()
        .get()
        .uri("/" + code)
        .header("X-Forwarded-For", ip)
        .header("Referer", referer)
        .retrieve()
        .onStatus(s -> true, (req, res) -> {})
        .toBodilessEntity();
  }

  record CreateReq(String url, String customAlias, String expiresAt) {}

  record LinkBody(String shortCode) {}

  record CreateBody(LinkBody link, String managementToken) {}
}
