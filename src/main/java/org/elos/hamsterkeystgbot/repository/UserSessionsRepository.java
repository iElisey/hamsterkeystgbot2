package org.elos.hamsterkeystgbot.repository;


import org.elos.hamsterkeystgbot.model.UserSessions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSessionsRepository extends JpaRepository<UserSessions, Long> {
    boolean existsByUserId(Long userId);
    Optional<UserSessions> findByUserId(Long userId);
}