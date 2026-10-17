package com.github.nhordiienko23.leaderboardservice.kafka;

import com.github.nhordiienko23.leaderboardcontract.event.LeaderboardEvent;
import com.github.nhordiienko23.leaderboardservice.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeaderboardEventListener {

    private final LeaderboardService leaderboardService;

    @KafkaListener(topics = KafkaTopics.LEADERBOARD_EVENTS)
    public void handleLeaderboardEvent(LeaderboardEvent event) {
        log.info(
                "Received leaderboard event: eventId={}, entityId={}, "
                        + "eventType={}, actionType={}",
                event.eventId(),
                event.entityId(),
                event.eventType(),
                event.actionType()
        );

        leaderboardService.processLeaderboardEvent(event);
    }
}