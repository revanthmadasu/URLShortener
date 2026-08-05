package com.example.urlshortener.config;

import com.example.urlshortener.common.ratelimit.RateLimitInterceptor;
import com.example.urlshortener.common.ratelimit.TokenBucketRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the rate-limit interceptor on the management API (write methods self-filter). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final TokenBucketRateLimiter limiter;
  private final AppProperties properties;
  private final MeterRegistry meterRegistry;

  public WebConfig(
      TokenBucketRateLimiter limiter, AppProperties properties, MeterRegistry meterRegistry) {
    this.limiter = limiter;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new RateLimitInterceptor(limiter, properties, meterRegistry))
        .addPathPatterns("/api/v1/**");
  }
}
