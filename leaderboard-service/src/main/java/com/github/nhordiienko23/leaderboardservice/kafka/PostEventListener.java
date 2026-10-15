package com.github.nhordiienko23.leaderboardservice.kafka;

import com.github.nhordiienko23.leaderboardservice.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PostEventListener {
    private final LeaderboardService leaderboardService;

    @KafkaListener(topics = KafkaTopics.POST_EVENTS)
    public void handlePostEvent(ContentEvent event) {
        log.info(
                "Received post event: eventId={}, userId={}, actionType={}",
                event.eventId(),
                event.userId(),
                event.actionType()
        );
        leaderboardService.processPostEvent(event);
    }
}
