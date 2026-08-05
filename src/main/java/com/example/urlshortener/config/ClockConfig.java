package com.example.urlshortener.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides an injectable UTC {@link Clock} so time-dependent logic (expiry) is testable. */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
