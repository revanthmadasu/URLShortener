package com.example.urlshortener.link.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.urlshortener.common.error.Errors;
import com.example.urlshortener.config.AppProperties;
import com.example.urlshortener.config.AppProperties.Cache;
import com.example.urlshortener.config.AppProperties.Code;
import com.example.urlshortener.config.AppProperties.Redirect;
import com.example.urlshortener.config.AppProperties.Security;
import com.example.urlshortener.support.TestFixtures;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeistelShortCodeGeneratorTest {

  @Mock private CodeSequence sequence;

  private static AppProperties propsWith(String alphabet, int length) {
    return new AppProperties(
        "http://localhost:8080",
        new Code(length, alphabet, Code.Strategy.FEISTEL, 2654435769L),
        new Cache(Duration.ofHours(1), Duration.ofSeconds(30)),
        new Security(true),
        new Redirect(List.of("http", "https"), true),
        new AppProperties.Analytics(true, "test-salt", Duration.ofDays(90)));
  }

  @Test
  void producesFixedLengthUniqueCodesForContiguousCounters() {
    AtomicLong counter = new AtomicLong(0);
    when(sequence.next()).thenAnswer(inv -> counter.getAndIncrement());
    FeistelShortCodeGenerator gen =
        new FeistelShortCodeGenerator(TestFixtures.appProperties(), sequence);

    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 5000; i++) {
      String code = gen.generate();
      assertThat(code).hasSize(7);
      codes.add(code);
    }
    // Unique by construction — no collisions across 5000 sequential counters.
    assertThat(codes).hasSize(5000);
  }

  @Test
  void coversEntireSmallKeyspaceWithoutCollision() {
    // Alphabet "ab", length 2 => capacity 4. Feed counters 0..3, expect 4 distinct codes.
    AtomicLong counter = new AtomicLong(0);
    when(sequence.next()).thenAnswer(inv -> counter.getAndIncrement());
    FeistelShortCodeGenerator gen = new FeistelShortCodeGenerator(propsWith("ab", 2), sequence);

    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 4; i++) {
      codes.add(gen.generate());
    }
    assertThat(codes).containsExactlyInAnyOrder("aa", "ab", "ba", "bb");
  }

  @Test
  void throwsWhenKeyspaceExhausted() {
    // Capacity 4; counter 4 is out of range => clean exhaustion error, not a crash.
    when(sequence.next()).thenReturn(4L);
    FeistelShortCodeGenerator gen = new FeistelShortCodeGenerator(propsWith("ab", 2), sequence);

    assertThatThrownBy(gen::generate).isInstanceOf(Errors.CodeExhausted.class);
  }
}
