package com.github.nhordiienko23.leaderboardservice.repository;

import com.github.nhordiienko23.leaderboardservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByEventIdAndEventTypeAndActionType(
      Long eventId,
      String eventType,
      String actionType
    );
}
