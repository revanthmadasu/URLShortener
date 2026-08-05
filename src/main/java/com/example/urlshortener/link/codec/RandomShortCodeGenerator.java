package com.example.urlshortener.link.codec;

import com.example.urlshortener.config.AppProperties;
import java.security.SecureRandom;

/**
 * Legacy random base-N generator, retained as a selectable strategy ({@code
 * app.code.strategy=random}) and as a baseline for comparison.
 *
 * <p>It can produce a code that already exists, so callers must handle the unique-constraint
 * violation and retry; as the keyspace fills, retries increase. The default strategy is {@link
 * FeistelShortCodeGenerator}, which avoids self-collision entirely.
 */
public class RandomShortCodeGenerator implements ShortCodeGenerator {

  private final SecureRandom random = new SecureRandom();
  private final char[] alphabet;
  private final int length;

  public RandomShortCodeGenerator(AppProperties properties) {
    this.alphabet = properties.code().alphabet().toCharArray();
    this.length = properties.code().length();
  }

  @Override
  public String generate() {
    char[] out = new char[length];
    for (int i = 0; i < length; i++) {
      out[i] = alphabet[random.nextInt(alphabet.length)];
    }
    return new String(out);
  }
}
