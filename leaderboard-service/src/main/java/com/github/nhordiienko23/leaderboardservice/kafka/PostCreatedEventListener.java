package com.github.nhordiienko23.leaderboardservice.kafka;

import com.github.nhordiienko23.leaderboardservice.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PostCreatedEventListener {
    private final LeaderboardService leaderboardService;
    @KafkaListener(topics = "post-created")
    public void handle(PostCreatedEvent event) {
        log.info(
                "Received PostCreatedEvent: postId={}, authorId={}",
                event.postId(),
                event.authorId()
        );
        leaderboardService.processPostCreated(event);
    }
}
