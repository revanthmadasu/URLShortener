package com.example.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.TestcontainersConfiguration;
import com.example.urlshortener.analytics.ClickEventRepository;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Full end-to-end flow against real Postgres + Redis (Testcontainers): create → redirect →
 * fetch → delete, plus the concurrent custom-alias race. Runs under Failsafe ({@code *IT});
 * requires Docker.
 */
@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LinkFlowIT {

  @LocalServerPort private int port;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickEventRepository;

  private RestClient client() {
    // Do not follow redirects: we assert on the 302 itself.
    return RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @AfterEach
  void cleanup() {
    linkRepository.deleteAll();
    // Redirects in this test record clicks asynchronously; clear them so other ITs stay isolated.
    clickEventRepository.deleteAll();
  }

  @Test
  void createRedirectFetchDelete() {
    // --- create ---
    ResponseEntity<CreateBody> created =
        client()
            .post()
            .uri("/api/v1/links")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateReq("https://example.com/landing", null, null))
            .retrieve()
            .toEntity(CreateBody.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String code = created.getBody().link().shortCode();
    String token = created.getBody().managementToken();
    assertThat(code).isNotBlank();
    assertThat(token).isNotBlank();

    // --- redirect (302, no auto-follow) ---
    ResponseEntity<Void> redirect =
        client().get().uri("/" + code).retrieve().toBodilessEntity();
    assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirect.getHeaders().getFirst(HttpHeaders.LOCATION))
        .isEqualTo("https://example.com/landing");

    // --- fetch metadata ---
    ResponseEntity<LinkBody> fetched =
        client().get().uri("/api/v1/links/" + code).retrieve().toEntity(LinkBody.class);
    assertThat(fetched.getBody().longUrl()).isEqualTo("https://example.com/landing");

    // --- delete without token → 403 ---
    ResponseEntity<Void> forbidden =
        client()
            .delete()
            .uri("/api/v1/links/" + code)
            .retrieve()
            .onStatus(s -> true, (req, res) -> {})
            .toBodilessEntity();
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // --- delete with token → 204 ---
    ResponseEntity<Void> deleted =
        client()
            .delete()
            .uri("/api/v1/links/" + code)
            .header("X-Management-Token", token)
            .retrieve()
            .toBodilessEntity();
    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // --- redirect now 404 ---
    ResponseEntity<Void> gone =
        client()
            .get()
            .uri("/" + code)
            .retrieve()
            .onStatus(s -> true, (req, res) -> {})
            .toBodilessEntity();
    assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void concurrentSameAliasYieldsExactlyOneWinner() throws Exception {
    int threads = 16;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Callable<Integer>> tasks =
          java.util.stream.IntStream.range(0, threads)
              .<Callable<Integer>>mapToObj(
                  i ->
                      () ->
                          client()
                              .post()
                              .uri("/api/v1/links")
                              .contentType(MediaType.APPLICATION_JSON)
                              .body(new CreateReq("https://example.com", "race-alias", null))
                              .retrieve()
                              .onStatus(s -> true, (req, res) -> {})
                              .toBodilessEntity()
                              .getStatusCode()
                              .value())
              .toList();

      List<Future<Integer>> results = pool.invokeAll(tasks);
      long created = 0;
      long conflicts = 0;
      for (Future<Integer> f : results) {
        int status = f.get();
        if (status == 201) {
          created++;
        } else if (status == 409) {
          conflicts++;
        }
      }
      // Exactly one create wins; the rest see 409. The DB unique index is the arbiter.
      assertThat(created).isEqualTo(1);
      assertThat(conflicts).isEqualTo(threads - 1);
      assertThat(linkRepository.existsByShortCode("race-alias")).isTrue();
    } finally {
      pool.shutdownNow();
    }
  }

  // Minimal request/response projections for the test client.
  record CreateReq(String url, String customAlias, String expiresAt) {}

  record LinkBody(String shortCode, String shortUrl, String longUrl) {}

  record CreateBody(LinkBody link, String managementToken) {}
}
