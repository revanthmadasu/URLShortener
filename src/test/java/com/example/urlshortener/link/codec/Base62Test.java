package com.example.urlshortener.link.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.support.TestFixtures;
import org.junit.jupiter.api.Test;

class Base62Test {

  private final Base62 base62 = new Base62(TestFixtures.ALPHABET, 7);

  @Test
  void encodesToFixedLength() {
    assertThat(base62.encode(0)).isEqualTo("0000000");
    assertThat(base62.encode(1)).hasSize(7);
    assertThat(base62.encode(61)).isEqualTo("000000Z");
    assertThat(base62.encode(62)).isEqualTo("0000010");
  }

  @Test
  void capacityIsBaseToTheLength() {
    // 62^7
    assertThat(base62.capacity()).isEqualTo(3_521_614_606_208L);
  }

  @Test
  void rejectsOutOfRange() {
    assertThatThrownBy(() -> base62.encode(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> base62.encode(base62.capacity()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBadConstruction() {
    assertThatThrownBy(() -> new Base62("x", 7)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Base62(TestFixtures.ALPHABET, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
