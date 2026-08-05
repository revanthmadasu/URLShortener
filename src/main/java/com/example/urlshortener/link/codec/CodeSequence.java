package com.example.urlshortener.link.codec;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Supplies the next value of the {@code link_code_seq} Postgres sequence. Sequence advancement
 * is non-transactional in Postgres (a fetched value is never reused, even on rollback), which
 * is exactly what the Feistel codec needs: a dense, ever-increasing, gap-tolerant counter.
 */
@Component
public class CodeSequence {

  private final JdbcTemplate jdbcTemplate;

  public CodeSequence(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public long next() {
    Long value = jdbcTemplate.queryForObject("SELECT nextval('link_code_seq')", Long.class);
    if (value == null) {
      throw new IllegalStateException("nextval('link_code_seq') returned null");
    }
    return value;
  }
}
