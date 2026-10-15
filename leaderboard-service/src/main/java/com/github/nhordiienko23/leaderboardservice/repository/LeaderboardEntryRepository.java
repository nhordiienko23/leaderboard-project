package com.github.nhordiienko23.leaderboardservice.repository;

import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeaderboardEntryRepository extends JpaRepository<LeaderboardEntry, Long> {
    Optional<LeaderboardEntry> findByUserId(Long userId);
}
