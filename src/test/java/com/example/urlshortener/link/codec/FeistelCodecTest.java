package com.example.urlshortener.link.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FeistelCodecTest {

  @Test
  void isBijectiveOverEntireSmallDomain() {
    long domain = 1000;
    FeistelCodec codec = new FeistelCodec(domain, 12345L);
    Set<Long> outputs = new HashSet<>();
    for (long i = 0; i < domain; i++) {
      long out = codec.encode(i);
      assertThat(out).isBetween(0L, domain - 1); // stays in domain (cycle-walking works)
      outputs.add(out);
    }
    // A bijection over [0, domain) must produce exactly `domain` distinct outputs.
    assertThat(outputs).hasSize((int) domain);
  }

  @ParameterizedTest(name = "bijective for non-power-of-two domain {0}")
  @ValueSource(longs = {2, 3, 7, 62, 100, 3844 /* 62^2 */, 12345})
  void isBijectiveForVariousDomains(long domain) {
    FeistelCodec codec = new FeistelCodec(domain, 99L);
    Set<Long> outputs = new HashSet<>();
    for (long i = 0; i < domain; i++) {
      outputs.add(codec.encode(i));
    }
    assertThat(outputs).hasSize((int) domain);
  }

  @Test
  void outputIsNonSequential() {
    FeistelCodec codec = new FeistelCodec(1_000_000, 7L);
    // Consecutive inputs should not map to consecutive outputs (that's the whole point).
    long a = codec.encode(0);
    long b = codec.encode(1);
    long c = codec.encode(2);
    assertThat(b - a).isNotEqualTo(1);
    assertThat(c - b).isNotEqualTo(1);
  }

  @Test
  void differentKeysGiveDifferentPermutations() {
    FeistelCodec k1 = new FeistelCodec(100_000, 1L);
    FeistelCodec k2 = new FeistelCodec(100_000, 2L);
    boolean anyDifferent = false;
    for (long i = 0; i < 50; i++) {
      if (k1.encode(i) != k2.encode(i)) {
        anyDifferent = true;
        break;
      }
    }
    assertThat(anyDifferent).isTrue();
  }

  @Test
  void rejectsOutOfRangeInput() {
    FeistelCodec codec = new FeistelCodec(100, 1L);
    assertThatThrownBy(() -> codec.encode(100)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> codec.encode(-1)).isInstanceOf(IllegalArgumentException.class);
  }
}
