package org.elos.hamsterkeystgbot.repository;

import org.elos.hamsterkeystgbot.model.Keys;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KeysRepository extends JpaRepository<Keys, Long> {
    List<Keys> findTop4ByPrefix(String prefix);
    List<Keys> findTop8ByPrefix(String prefix);

    @Query("SELECT k.prefix, COUNT(k) as count FROM Keys k GROUP BY k.prefix ORDER BY count DESC")
    List<Object[]> countKeysByPrefix();

    long countByPrefix(String prefix);
}