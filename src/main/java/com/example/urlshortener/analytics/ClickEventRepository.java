package com.example.urlshortener.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

  @Query("select count(c) from ClickEvent c where c.shortCode = :code and c.occurredAt >= :since")
  long countSince(@Param("code") String code, @Param("since") Instant since);

  @Query(
      "select count(distinct c.ipHash) from ClickEvent c "
          + "where c.shortCode = :code and c.occurredAt >= :since and c.ipHash is not null")
  long countDistinctVisitorsSince(@Param("code") String code, @Param("since") Instant since);

  /** Per-UTC-day click counts since {@code since}, ascending by day. */
  @Query(
      value =
          "select date_trunc('day', occurred_at at time zone 'UTC')::date as day, "
              + "count(*) as count "
              + "from click_events "
              + "where short_code = :code and occurred_at >= :since "
              + "group by day order by day",
      nativeQuery = true)
  List<DailyCount> clicksByDay(@Param("code") String code, @Param("since") Instant since);

  /** Top referrers since {@code since}, most frequent first. */
  @Query(
      value =
          "select coalesce(referer, '(none)') as referer, count(*) as count "
              + "from click_events "
              + "where short_code = :code and occurred_at >= :since "
              + "group by referer order by count desc, referer asc limit :limit",
      nativeQuery = true)
  List<ReferrerCount> topReferrers(
      @Param("code") String code, @Param("since") Instant since, @Param("limit") int limit);

  /** Retention sweep: delete events older than the cutoff. */
  @Modifying
  @Query("delete from ClickEvent c where c.occurredAt < :cutoff")
  int deleteOlderThan(@Param("cutoff") Instant cutoff);

  /** Projection for {@link #clicksByDay}. */
  interface DailyCount {
    LocalDate getDay();

    long getCount();
  }

  /** Projection for {@link #topReferrers}. */
  interface ReferrerCount {
    String getReferer();

    long getCount();
  }
}
