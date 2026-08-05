package com.example.urlshortener.common.ratelimit;

import com.example.urlshortener.common.error.Errors;
import com.example.urlshortener.config.AppProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the {@link TokenBucketRateLimiter} to mutating requests (POST/DELETE) on management APIs,
 * keyed by client IP. Redirects and reads are not limited here. On exhaustion it throws {@link
 * Errors.RateLimited} (mapped to 429 with a {@code Retry-After} header).
 *
 * <p>Without authentication the key is the client IP, which is spoofable via IP rotation — this is
 * a first line of defense, not a complete control (risk R2). The real fix is per-API-key limits,
 * out of the chosen "no auth" scope.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

  private final TokenBucketRateLimiter limiter;
  private final boolean enabled;
  private final Counter rejected;

  public RateLimitInterceptor(
      TokenBucketRateLimiter limiter, AppProperties properties, MeterRegistry meterRegistry) {
    this.limiter = limiter;
    this.enabled = properties.rateLimit().enabled();
    this.rejected = Counter.builder("urlshortener.ratelimit.rejected").register(meterRegistry);
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!enabled || isSafeMethod(request.getMethod())) {
      return true;
    }
    String key = clientIp(request);
    if (!limiter.tryAcquire(key)) {
      rejected.increment();
      response.setHeader("Retry-After", "1");
      throw new Errors.RateLimited("Rate limit exceeded; slow down and retry shortly");
    }
    return true;
  }

  private static boolean isSafeMethod(String method) {
    return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
  }

  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
    }
    return request.getRemoteAddr();
  }
}
