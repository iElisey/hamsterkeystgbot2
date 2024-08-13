package org.elos.hamsterkeystgbot.repository;

import org.elos.hamsterkeystgbot.model.UserReferrals;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserReferralsRepository extends JpaRepository<UserReferrals, Long> {
    List<UserReferrals> findByReferrerId(Long referrerId);
}