package com.example.urlshortener.link.codec;

import com.example.urlshortener.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the active {@link ShortCodeGenerator} strategy from {@code app.code.strategy}. Keeping
 * selection in one place (rather than scattering {@code @ConditionalOnProperty} across the
 * implementations) makes the choice explicit and the default obvious.
 */
@Configuration
public class CodecConfig {

  private static final Logger log = LoggerFactory.getLogger(CodecConfig.class);

  @Bean
  public ShortCodeGenerator shortCodeGenerator(AppProperties properties, CodeSequence sequence) {
    AppProperties.Code.Strategy strategy = properties.code().strategy();
    log.info("Short-code generation strategy: {}", strategy);
    return switch (strategy) {
      case FEISTEL -> new FeistelShortCodeGenerator(properties, sequence);
      case RANDOM -> new RandomShortCodeGenerator(properties);
    };
  }
}
