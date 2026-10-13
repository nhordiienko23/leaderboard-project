package com.github.nhordiienko23.leaderboardservice.repository;

import com.github.nhordiienko23.leaderboardservice.entity.ProcessedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedPostRepository extends JpaRepository<ProcessedPost, Long> {
}
