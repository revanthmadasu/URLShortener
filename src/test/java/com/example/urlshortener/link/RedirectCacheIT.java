package com.example.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the redirect path is genuinely cache-aside against real Redis + Postgres: once a code is
 * cached, a redirect is served even after the underlying row is removed out-of-band (i.e. without
 * going through {@code LinkService.delete}, which would evict). Requires Docker.
 */
@Tag("integration")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RedirectCacheIT {

  @Autowired private LinkService linkService;
  @Autowired private LinkRepository linkRepository;
  @Autowired private StringRedisTemplate redis;

  @AfterEach
  void cleanup() {
    linkRepository.deleteAll();
    redis.getConnectionFactory().getConnection().serverCommands().flushAll();
  }

  @Test
  @Transactional
  void redirectIsServedFromCacheAfterRowRemovedOutOfBand() {
    var created =
        linkService.create(
            new com.example.urlshortener.link.dto.CreateLinkRequest(
                "https://example.com/cached", null, null));
    String code = created.link().getShortCode();

    // First resolve: cache miss → DB → populates cache.
    assertThat(linkService.resolveTargetUrl(code)).isEqualTo("https://example.com/cached");

    // Remove the row directly (bypassing LinkService.delete, so the cache is NOT evicted).
    linkRepository.deleteByShortCode(code);
    linkRepository.flush();

    // Still resolves — proving the value came from the cache, not the (now-empty) table.
    assertThat(linkService.resolveTargetUrl(code)).isEqualTo("https://example.com/cached");

    // Explicit eviction then makes it a genuine miss → 404.
    redis.delete("url:" + code);
    assertThat(linkRepository.existsByShortCode(code)).isFalse();
  }
}
