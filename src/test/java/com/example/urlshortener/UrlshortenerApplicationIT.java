package com.example.urlshortener;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Smoke integration test: verifies the full application context boots against real Postgres and
 * Redis (via Testcontainers). Requires Docker. Runs under the Failsafe plugin ({@code *IT}) so
 * plain {@code ./mvnw test} stays green without a Docker daemon.
 */
@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UrlshortenerApplicationIT {

  @Test
  void contextLoads() {}
}
