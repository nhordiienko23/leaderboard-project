package com.github.nhordiienko23.leaderboardservice.repository;

import com.github.nhordiienko23.leaderboardservice.entity.ProcessedUserEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedUserEventRepository extends JpaRepository<ProcessedUserEvent, Long> {
}