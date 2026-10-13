package com.github.nhordiienko23.leaderboardservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PostCreatedEventListener {
    @KafkaListener(topics = "post-created")
    public void handle(PostCreatedEvent event) {
        log.info(
                "Received PostCreatedEvent: postId={}, authorId={}",
                event.postId(),
                event.authorId()
        );
    }
}
