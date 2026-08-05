package com.example.urlshortener.link;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkRepository extends JpaRepository<Link, Long> {

  Optional<Link> findByShortCode(String shortCode);

  boolean existsByShortCode(String shortCode);

  @Modifying
  @Query("delete from Link l where l.shortCode = :shortCode")
  int deleteByShortCode(@Param("shortCode") String shortCode);
}
