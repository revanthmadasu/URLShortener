package com.example.urlshortener.link.codec;

/**
 * Strategy for producing short codes. Two implementations exist:
 *
 * <ul>
 *   <li>{@code RandomShortCodeGenerator} — random base62; may self-collide, needs retry.
 *   <li>{@code FeistelShortCodeGenerator} — sequence + Feistel permutation; unique by construction,
 *       non-sequential output.
 * </ul>
 *
 * Selected via {@code app.code.strategy}. Regardless of strategy, the database unique index remains
 * the authority on uniqueness, because generated codes and user-supplied custom aliases share one
 * namespace (see the brownfield scenario notes).
 */
public interface ShortCodeGenerator {

  /** Returns the next candidate short code. Uniqueness is enforced by the database. */
  String generate();
}
