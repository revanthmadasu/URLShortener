package com.example.urlshortener.config;

import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables asynchronous click capture and scheduled retention sweeps. The click executor uses
 * virtual threads: click writes are short, blocking JDBC operations, and one cheap virtual thread
 * per task keeps capture off the redirect path without a bounded pool becoming a bottleneck.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

  @Bean("clickExecutor")
  public AsyncTaskExecutor clickExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }
}
