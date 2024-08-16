package org.elos.hamsterkeystgbot.repository;

import org.elos.hamsterkeystgbot.model.Keys;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KeysRepository extends JpaRepository<Keys, Long> {
    List<Keys> findTop4ByPrefix(String prefix);
    List<Keys> findTop8ByPrefix(String prefix);

    long countByPrefix(String prefix);
}