package com.github.nhordiienko23.postservice.repository;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {

    List<OutboxEntry> findTop100ByPublishedFalseOrderByIdAsc();

    boolean existsByContentIdAndEventTypeAndActionType(
            Long contentId,
            EventType eventType,
            ActionType actionType
    );
}