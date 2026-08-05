package com.example.urlshortener.link;

import com.example.urlshortener.common.error.Errors;
import com.example.urlshortener.common.security.ManagementTokenService;
import com.example.urlshortener.common.security.ManagementTokenService.IssuedToken;
import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.link.codec.ShortCodeGenerator;
import com.example.urlshortener.link.dto.CreateLinkRequest;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Application service for the link lifecycle: create, resolve (for redirect), fetch, delete.
 *
 * <p><b>Concurrency & correctness.</b> The database unique index on {@code short_code} is the
 * single arbiter of code uniqueness. This service never does check-then-insert; it inserts and
 * treats a {@link DataIntegrityViolationException} as authoritative:
 *
 * <ul>
 *   <li>For a caller-supplied <em>custom alias</em>, a violation means the alias is taken → 409.
 *   <li>For a <em>generated</em> code, a violation means a rare collision → regenerate and retry.
 * </ul>
 *
 * Each {@code saveAndFlush} runs in its own repository-managed transaction, so a failed attempt
 * rolls back cleanly and the next attempt starts fresh. This is why {@code create} is not
 * annotated {@code @Transactional}: an enclosing transaction would be poisoned by the first
 * constraint violation and could not retry.
 */
@Service
public class LinkService {

  private static final Logger log = LoggerFactory.getLogger(LinkService.class);

  /** Bounded retry budget for generated-code collisions. */
  private static final int MAX_GENERATION_ATTEMPTS = 5;

  private final LinkRepository repository;
  private final ShortCodeGenerator codeGenerator;
  private final UrlValidator urlValidator;
  private final ManagementTokenService tokenService;
  private final AppProperties properties;
  private final Clock clock;

  public LinkService(
      LinkRepository repository,
      ShortCodeGenerator codeGenerator,
      UrlValidator urlValidator,
      ManagementTokenService tokenService,
      AppProperties properties,
      Clock clock) {
    this.repository = repository;
    this.codeGenerator = codeGenerator;
    this.urlValidator = urlValidator;
    this.tokenService = tokenService;
    this.properties = properties;
    this.clock = clock;
  }

  /** Result of a successful create: the persisted link plus the one-time management token. */
  public record CreateResult(Link link, String managementToken) {}

  public CreateResult create(CreateLinkRequest request) {
    String normalizedUrl = urlValidator.validate(request.url());
    Instant expiresAt = validateExpiry(request.expiresAt());
    IssuedToken token = tokenService.issue();

    Link saved =
        (request.customAlias() != null && !request.customAlias().isBlank())
            ? insertWithAlias(request.customAlias(), normalizedUrl, token.hash(), expiresAt)
            : insertWithGeneratedCode(normalizedUrl, token.hash(), expiresAt);

    return new CreateResult(saved, token.token());
  }

  private Link insertWithAlias(String alias, String url, String tokenHash, Instant expiresAt) {
    try {
      return repository.saveAndFlush(Link.create(alias, url, tokenHash, clock.instant(), expiresAt));
    } catch (DataIntegrityViolationException e) {
      throw new Errors.AliasConflict(alias);
    }
  }

  private Link insertWithGeneratedCode(String url, String tokenHash, Instant expiresAt) {
    for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
      String code = codeGenerator.generate();
      try {
        return repository.saveAndFlush(Link.create(code, url, tokenHash, clock.instant(), expiresAt));
      } catch (DataIntegrityViolationException e) {
        log.warn("Short-code collision on '{}' (attempt {}/{})", code, attempt, MAX_GENERATION_ATTEMPTS);
      }
    }
    throw new Errors.CodeExhausted(
        "Could not generate a unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts");
  }

  /** Resolve a code to its destination for redirection. Throws 404 if unknown, 410 if expired. */
  public Link resolveForRedirect(String code) {
    Link link = repository.findByShortCode(code).orElseThrow(() -> notFound(code));
    if (link.isExpired(clock.instant())) {
      throw new Errors.Gone("Link '" + code + "' has expired");
    }
    return link;
  }

  /** Fetch link metadata. Throws 404 if unknown. Expired links are still returned as metadata. */
  public Link getByCode(String code) {
    return repository.findByShortCode(code).orElseThrow(() -> notFound(code));
  }

  /**
   * Delete a link. When {@code app.security.require-management-token} is enabled, a matching
   * token must be supplied. Returns quietly on success; throws 404 if the code is unknown and
   * 403 if the token is required but missing/incorrect.
   */
  public void delete(String code, String presentedToken) {
    Link link = repository.findByShortCode(code).orElseThrow(() -> notFound(code));
    if (properties.security().requireManagementToken()) {
      if (!tokenService.matches(presentedToken, link.getManagementTokenHash())) {
        throw new Errors.Forbidden("A valid management token is required to delete this link");
      }
    }
    repository.delete(link);
  }

  private Instant validateExpiry(Instant expiresAt) {
    if (expiresAt == null) {
      return null;
    }
    if (!expiresAt.isAfter(clock.instant())) {
      throw new Errors.InvalidExpiry("expiresAt must be in the future");
    }
    return expiresAt;
  }

  private static Errors.NotFound notFound(String code) {
    return new Errors.NotFound("No link found for code '" + code + "'");
  }
}
