package com.example.urlshortener;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
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
}
