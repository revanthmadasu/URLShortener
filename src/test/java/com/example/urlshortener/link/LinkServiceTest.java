package com.example.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.urlshortener.common.error.Errors;
import com.example.urlshortener.common.metrics.AppMetrics;
import com.example.urlshortener.common.security.ManagementTokenService;
import com.example.urlshortener.common.security.ManagementTokenService.IssuedToken;
import com.example.urlshortener.link.LinkService.CreateResult;
import com.example.urlshortener.link.codec.ShortCodeGenerator;
import com.example.urlshortener.link.dto.CreateLinkRequest;
import com.example.urlshortener.support.TestFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

  @Mock private LinkRepository repository;
  @Mock private ShortCodeGenerator codeGenerator;
  @Mock private RedirectCache cache;

  private LinkService service;
  private ManagementTokenService tokenService;

  @BeforeEach
  void setUp() {
    tokenService = new ManagementTokenService();
    service =
        new LinkService(
            repository,
            codeGenerator,
            TestFixtures.urlValidator(),
            tokenService,
            cache,
            new AppMetrics(new SimpleMeterRegistry()),
            TestFixtures.appProperties(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static CreateLinkRequest req(String url, String alias, Instant expiresAt) {
    return new CreateLinkRequest(url, alias, expiresAt);
  }

  /** Make the repository echo back the entity it was asked to persist. */
  private void repoEchoesOnSave() {
    when(repository.saveAndFlush(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Nested
  class Create {

    @Test
    void generatesCodeWhenNoAliasGiven() {
      when(codeGenerator.generate()).thenReturn("abc1234");
      repoEchoesOnSave();

      CreateResult result = service.create(req("https://example.com", null, null));

      assertThat(result.link().getShortCode()).isEqualTo("abc1234");
      assertThat(result.link().getLongUrl()).isEqualTo("https://example.com");
      assertThat(result.managementToken()).isNotBlank();
      // The stored hash must correspond to the returned token, and not equal it.
      assertThat(
              tokenService.matches(
                  result.managementToken(), result.link().getManagementTokenHash()))
          .isTrue();
    }

    @Test
    void usesCustomAliasWhenProvided() {
      repoEchoesOnSave();

      CreateResult result = service.create(req("https://example.com", "my-alias", null));

      assertThat(result.link().getShortCode()).isEqualTo("my-alias");
      verifyNoInteractions(codeGenerator);
    }

    @Test
    void aliasConflictBecomes409() {
      when(repository.saveAndFlush(any(Link.class)))
          .thenThrow(new DataIntegrityViolationException("dup"));

      assertThatThrownBy(() -> service.create(req("https://example.com", "taken", null)))
          .isInstanceOf(Errors.AliasConflict.class);
    }

    @Test
    void retriesOnGeneratedCodeCollisionThenSucceeds() {
      when(codeGenerator.generate()).thenReturn("collide", "unique1");
      when(repository.saveAndFlush(any(Link.class)))
          .thenThrow(new DataIntegrityViolationException("dup"))
          .thenAnswer(inv -> inv.getArgument(0));

      CreateResult result = service.create(req("https://example.com", null, null));

      assertThat(result.link().getShortCode()).isEqualTo("unique1");
      verify(repository, times(2)).saveAndFlush(any(Link.class));
    }

    @Test
    void exhaustingRetriesBecomes409() {
      when(codeGenerator.generate()).thenReturn("x");
      when(repository.saveAndFlush(any(Link.class)))
          .thenThrow(new DataIntegrityViolationException("dup"));

      assertThatThrownBy(() -> service.create(req("https://example.com", null, null)))
          .isInstanceOf(Errors.CodeExhausted.class);
      verify(repository, times(5)).saveAndFlush(any(Link.class));
    }

    @Test
    void rejectsInvalidUrl() {
      assertThatThrownBy(() -> service.create(req("javascript:alert(1)", null, null)))
          .isInstanceOf(Errors.InvalidUrl.class);
      verifyNoInteractions(repository);
    }

    @Test
    void rejectsPastExpiry() {
      assertThatThrownBy(
              () -> service.create(req("https://example.com", null, NOW.minusSeconds(1))))
          .isInstanceOf(Errors.InvalidExpiry.class);
      verifyNoInteractions(repository);
    }

    @Test
    void acceptsFutureExpiry() {
      repoEchoesOnSave();
      when(codeGenerator.generate()).thenReturn("abc1234");

      CreateResult result = service.create(req("https://example.com", null, NOW.plusSeconds(3600)));

      assertThat(result.link().getExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }
  }

  @Nested
  class Resolve {

    @Test
    void returnsLinkForRedirect() {
      Link link = Link.create("abc1234", "https://example.com", "hash", NOW, null);
      when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(link));

      assertThat(service.resolveForRedirect("abc1234")).isSameAs(link);
    }

    @Test
    void unknownCodeBecomes404() {
      when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.resolveForRedirect("missing"))
          .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void expiredLinkBecomes410() {
      Link expired =
          Link.create(
              "abc1234", "https://example.com", "hash", NOW.minusSeconds(10), NOW.minusSeconds(1));
      when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(expired));

      assertThatThrownBy(() -> service.resolveForRedirect("abc1234"))
          .isInstanceOf(Errors.Gone.class);
    }

    @Test
    void getByCodeReturnsExpiredLinkAsMetadata() {
      Link expired =
          Link.create(
              "abc1234", "https://example.com", "hash", NOW.minusSeconds(10), NOW.minusSeconds(1));
      when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(expired));

      assertThat(service.getByCode("abc1234")).isSameAs(expired);
    }
  }

  @Nested
  class ResolveTargetUrl {

    @Test
    void cacheHitSkipsDatabase() {
      when(cache.lookup("abc1234")).thenReturn(RedirectCache.Lookup.hit("https://cached.example"));

      assertThat(service.resolveTargetUrl("abc1234")).isEqualTo("https://cached.example");
      verifyNoInteractions(repository);
    }

    @Test
    void cacheNegativeThrowsNotFoundWithoutDatabase() {
      when(cache.lookup("missing")).thenReturn(RedirectCache.Lookup.negative());

      assertThatThrownBy(() -> service.resolveTargetUrl("missing"))
          .isInstanceOf(Errors.NotFound.class);
      verifyNoInteractions(repository);
    }

    @Test
    void cacheMissLoadsFromDbAndPopulatesPositive() {
      when(cache.lookup("abc1234")).thenReturn(RedirectCache.Lookup.miss());
      Link link = Link.create("abc1234", "https://example.com", "hash", NOW, null);
      when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(link));

      assertThat(service.resolveTargetUrl("abc1234")).isEqualTo("https://example.com");
      verify(cache).putPositive(eq("abc1234"), eq("https://example.com"), any(Duration.class));
    }

    @Test
    void cacheMissUnknownPopulatesNegative() {
      when(cache.lookup("missing")).thenReturn(RedirectCache.Lookup.miss());
      when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.resolveTargetUrl("missing"))
          .isInstanceOf(Errors.NotFound.class);
      verify(cache).putNegative(eq("missing"), any(Duration.class));
    }

    @Test
    void expiredLinkIsNeverCachedPositively() {
      when(cache.lookup("abc1234")).thenReturn(RedirectCache.Lookup.miss());
      Link expired =
          Link.create(
              "abc1234", "https://example.com", "hash", NOW.minusSeconds(10), NOW.minusSeconds(1));
      when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(expired));

      assertThatThrownBy(() -> service.resolveTargetUrl("abc1234")).isInstanceOf(Errors.Gone.class);
      verify(cache, never()).putPositive(any(), any(), any());
    }

    @Test
    void positiveTtlIsBoundedByRemainingLifetime() {
      when(cache.lookup("abc1234")).thenReturn(RedirectCache.Lookup.miss());
      // Expires in 10 minutes; the default cache TTL is 1 hour, so TTL must be clamped to ~10m.
      Instant expiresAt = NOW.plusSeconds(600);
      Link link = Link.create("abc1234", "https://example.com", "hash", NOW, expiresAt);
      when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(link));

      service.resolveTargetUrl("abc1234");

      org.mockito.ArgumentCaptor<Duration> ttl =
          org.mockito.ArgumentCaptor.forClass(Duration.class);
      verify(cache).putPositive(eq("abc1234"), eq("https://example.com"), ttl.capture());
      assertThat(ttl.getValue()).isLessThanOrEqualTo(Duration.ofSeconds(600));
    }
  }

  @Nested
  class Delete {

    @Test
    void deletesWithValidToken() {
      IssuedToken issued = tokenService.issue();
      Link link = Link.create("abc1234", "https://example.com", issued.hash(), NOW, null);
      when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(link));

      service.delete("abc1234", issued.token());

      verify(repository).delete(link);
    }

    @Test
    void rejectsInvalidToken() {
      Link link =
          Link.create("abc1234", "https://example.com", tokenService.issue().hash(), NOW, null);
      when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(link));

      assertThatThrownBy(() -> service.delete("abc1234", "wrong-token"))
          .isInstanceOf(Errors.Forbidden.class);
      verify(repository, times(0)).delete(any());
    }

    @Test
    void unknownCodeBecomes404() {
      when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.delete("missing", "tok"))
          .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void skipsTokenCheckWhenDisabled() {
      LinkService open =
          new LinkService(
              repository,
              codeGenerator,
              TestFixtures.urlValidator(),
              tokenService,
              cache,
              new AppMetrics(new SimpleMeterRegistry()),
              TestFixtures.appProperties(7, false),
              Clock.fixed(NOW, ZoneOffset.UTC));
      Link link = Link.create("abc1234", "https://example.com", "hash", NOW, null);
      when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(link));

      open.delete("abc1234", null);

      verify(repository).delete(link);
    }
  }
}
