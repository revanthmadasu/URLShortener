package com.example.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.support.TestFixtures;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomShortCodeGeneratorTest {

  @Test
  void generatesCodesOfConfiguredLengthFromAlphabet() {
    RandomShortCodeGenerator gen = new RandomShortCodeGenerator(TestFixtures.appProperties(7, true));
    for (int i = 0; i < 1000; i++) {
      String code = gen.generate();
      assertThat(code).hasSize(7);
      assertThat(code.chars()).allMatch(c -> TestFixtures.ALPHABET.indexOf(c) >= 0);
    }
  }

  @Test
  void producesHighlyUniqueCodes() {
    RandomShortCodeGenerator gen = new RandomShortCodeGenerator(TestFixtures.appProperties(7, true));
    Set<String> seen = new HashSet<>();
    int n = 10_000;
    for (int i = 0; i < n; i++) {
      seen.add(gen.generate());
    }
    // With a 62^7 keyspace, 10k draws should essentially never collide. Allow a tiny margin.
    assertThat(seen.size()).isGreaterThan(n - 5);
  }

  @Test
  void respectsCustomLength() {
    RandomShortCodeGenerator gen = new RandomShortCodeGenerator(TestFixtures.appProperties(4, true));
    assertThat(gen.generate()).hasSize(4);
  }
}
