package com.example.urlshortener.link;

import com.example.urlshortener.config.AppProperties;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates random base-N short codes from the configured alphabet.
 *
 * <p><b>Design note (intentional, revisited in Phase 2).</b> This is the naive
 * "random + collision-retry" strategy: it can, in principle, generate a code that already
 * exists, so callers must handle the unique-constraint violation and retry. As the keyspace
 * fills, retries increase. Phase 2 (brownfield) replaces the collision-prone approach with a
 * sequence + Feistel permutation behind a {@code ShortCodeGenerator} interface, which is
 * collision-free by construction. This class is deliberately wired concretely for now so the
 * refactor has a real seam to extract.
 */
@Component
public class RandomShortCodeGenerator {

  private final SecureRandom random = new SecureRandom();
  private final char[] alphabet;
  private final int length;

  public RandomShortCodeGenerator(AppProperties properties) {
    this.alphabet = properties.code().alphabet().toCharArray();
    this.length = properties.code().length();
  }

  /** Returns a fresh random code. Uniqueness is enforced by the database, not here. */
  public String generate() {
    char[] out = new char[length];
    for (int i = 0; i < length; i++) {
      out[i] = alphabet[random.nextInt(alphabet.length)];
    }
    return new String(out);
  }
}
