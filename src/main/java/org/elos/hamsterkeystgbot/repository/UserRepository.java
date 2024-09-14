package org.elos.hamsterkeystgbot.repository;


import jakarta.transaction.Transactional;
import org.elos.hamsterkeystgbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUserId(Long userId);

    Optional<User> findByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.receivedNewKeys = false")
    void resetReceivedNewKeysForAll();
}