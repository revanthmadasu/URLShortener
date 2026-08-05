package com.example.urlshortener.link.codec;

/**
 * Format-preserving permutation of a dense counter into a non-sequential code value, using a
 * balanced <b>Feistel network</b> plus <b>cycle-walking</b> to stay within an arbitrary domain.
 *
 * <p><b>Why.</b> We want short codes that are (a) unique by construction — so no collision retries
 * as the keyspace fills — and (b) non-sequential, so a public code does not leak the creation order
 * or the total number of links. A Feistel network is a bijection over {@code [0, 2^bits)} for
 * <i>any</i> round function, which gives uniqueness for free. Cycle- walking restricts that
 * bijection to {@code [0, domain)} where {@code domain = alphabet^length} is not a power of two.
 *
 * <p><b>Not cryptography.</b> The round function is a fast mixing function, not a secure PRF. Codes
 * are public identifiers, not secrets; the only goal is non-sequentiality.
 *
 * <p>The mapping is a bijection, so {@code encode} is injective: distinct inputs in {@code [0,
 * domain)} always yield distinct outputs in {@code [0, domain)}.
 */
public final class FeistelCodec {

  private static final int ROUNDS = 4;

  private final long domain;
  private final int halfBits;
  private final long halfMask;
  private final long key;

  /**
   * @param domain number of representable values ({@code alphabet^length}); must be >= 2
   * @param key parameterizes the permutation (changes the ordering, not the bijection property)
   */
  public FeistelCodec(long domain, long key) {
    if (domain < 2) {
      throw new IllegalArgumentException("domain must be >= 2");
    }
    this.domain = domain;
    // Smallest even bit-width whose 2^bits >= domain, so cycle-walking terminates quickly.
    int bits = 64 - Long.numberOfLeadingZeros(domain - 1);
    if (bits % 2 != 0) {
      bits++;
    }
    this.halfBits = bits / 2;
    this.halfMask = (1L << halfBits) - 1;
    this.key = key;
  }

  /** Maps a counter in {@code [0, domain)} to a permuted value in {@code [0, domain)}. */
  public long encode(long index) {
    if (index < 0 || index >= domain) {
      throw new IllegalArgumentException("index out of range: " + index);
    }
    long x = permute(index);
    // Cycle-walk: the permutation acts on [0, 2^bits); skip any output that lands outside the
    // (smaller) domain. Because the permutation is a bijection, this terminates and remains a
    // bijection on [0, domain).
    while (x >= domain) {
      x = permute(x);
    }
    return x;
  }

  private long permute(long input) {
    long left = (input >>> halfBits) & halfMask;
    long right = input & halfMask;
    for (int round = 0; round < ROUNDS; round++) {
      long next = left ^ roundFunction(right, round);
      left = right;
      right = next;
    }
    return (left << halfBits) | right;
  }

  /** Fast, deterministic mixing (finalizer-style), reduced to the half-block width. */
  private long roundFunction(long right, int round) {
    long h = right ^ (key + round * 0x9E3779B97F4A7C15L);
    h ^= (h >>> 30);
    h *= 0xBF58476D1CE4E5B9L;
    h ^= (h >>> 27);
    h *= 0x94D049BB133111EBL;
    h ^= (h >>> 31);
    return h & halfMask;
  }
}
