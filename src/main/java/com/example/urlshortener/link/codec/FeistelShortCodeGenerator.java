package com.example.urlshortener.link.codec;

import com.example.urlshortener.common.error.Errors;
import com.example.urlshortener.config.AppProperties;

/**
 * Collision-free generator: {@code base62( feistel( nextval(link_code_seq) ) )}.
 *
 * <p>Because the sequence yields distinct counters and the Feistel codec is a bijection, every call
 * produces a distinct code until the entire {@code alphabet^length} keyspace is consumed — at which
 * point creation fails cleanly rather than looping. Custom aliases still share the namespace, so
 * {@code LinkService} keeps the DB-arbitrated retry (a fresh call here yields the next counter and
 * thus a different code).
 */
public class FeistelShortCodeGenerator implements ShortCodeGenerator {

  private final CodeSequence sequence;
  private final FeistelCodec codec;
  private final Base62 base62;
  private final long capacity;

  public FeistelShortCodeGenerator(AppProperties properties, CodeSequence sequence) {
    this.sequence = sequence;
    this.base62 = new Base62(properties.code().alphabet(), properties.code().length());
    this.capacity = base62.capacity();
    this.codec = new FeistelCodec(capacity, properties.code().feistelKey());
  }

  @Override
  public String generate() {
    long counter = sequence.next();
    if (counter >= capacity) {
      throw new Errors.CodeExhausted("Short-code keyspace of " + capacity + " codes is exhausted");
    }
    return base62.encode(codec.encode(counter));
  }
}
