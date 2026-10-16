package com.github.nhordiienko23.leaderboardservice.repository;

import com.github.nhordiienko23.leaderboardservice.entity.RedisSyncOutbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RedisSyncOutboxRepository extends JpaRepository<RedisSyncOutbox, Long> {

    @Transactional(readOnly = true)
    List<RedisSyncOutbox> findTop100ByProcessedFalseOrderByIdAsc();
}