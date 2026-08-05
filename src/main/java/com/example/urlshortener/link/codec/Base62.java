package com.example.urlshortener.link.codec;

/**
 * Fixed-length base-N encoding over a caller-supplied alphabet. Used to render a numeric code
 * value as a short string of exactly {@code length} characters (left-padded with the first
 * alphabet symbol), so all generated codes share a uniform width.
 */
public final class Base62 {

  private final char[] alphabet;
  private final int base;
  private final int length;

  public Base62(String alphabet, int length) {
    if (alphabet == null || alphabet.length() < 2) {
      throw new IllegalArgumentException("alphabet must have at least 2 symbols");
    }
    if (length < 1) {
      throw new IllegalArgumentException("length must be >= 1");
    }
    this.alphabet = alphabet.toCharArray();
    this.base = alphabet.length();
    this.length = length;
  }

  /** The number of distinct codes representable: {@code base^length}. */
  public long capacity() {
    long cap = 1;
    for (int i = 0; i < length; i++) {
      cap *= base;
    }
    return cap;
  }

  /** Encodes {@code value} into exactly {@code length} symbols. Requires 0 <= value < capacity. */
  public String encode(long value) {
    if (value < 0 || value >= capacity()) {
      throw new IllegalArgumentException("value out of range for fixed-length encoding: " + value);
    }
    char[] out = new char[length];
    long v = value;
    for (int i = length - 1; i >= 0; i--) {
      out[i] = alphabet[(int) (v % base)];
      v /= base;
    }
    return new String(out);
  }
}
