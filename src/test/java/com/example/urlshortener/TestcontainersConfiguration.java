package com.example.urlshortener;

import com.example.urlshortener.link.PrivateNetworkGuard;
import java.net.InetAddress;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers wiring for integration tests. Image versions are pinned to the same
 * versions used in {@code docker-compose.yml} so tests exercise the real target runtime.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    // Note: in this Testcontainers version org.testcontainers.postgresql.PostgreSQLContainer
    // is a non-generic class, so no type parameter here.
    return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
  }

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
  }

  /**
   * Override the real DNS-resolving guard with a deterministic one for integration tests: every
   * host resolves to a public address, so tests never depend on external DNS. The private-network
   * range logic itself is covered hermetically by {@code UrlValidatorTest.PrivateNetworkBlocking}.
   */
  @Bean
  @Primary
  PrivateNetworkGuard testPrivateNetworkGuard() {
    return new PrivateNetworkGuard(host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
  }
}
