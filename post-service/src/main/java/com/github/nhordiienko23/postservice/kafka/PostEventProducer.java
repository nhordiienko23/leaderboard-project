package com.github.nhordiienko23.postservice.kafka;

import com.github.nhordiienko23.leaderboardcontract.event.LeaderboardEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventProducer {

    private final KafkaTemplate<String, LeaderboardEvent> kafkaTemplate;

    public void publish(String topic, LeaderboardEvent event) {
        String key = event.eventType() + ":" + event.entityId();

        try {
            SendResult<String, LeaderboardEvent> result = kafkaTemplate
                    .send(topic, key, event)
                    .get();

            var metadata = result.getRecordMetadata();

            log.info(
                    "Leaderboard event published: topic={}, key={}, partition={}, offset={}, eventId={}, eventType={}, actionType={}, reason={}",
                    metadata.topic(),
                    key,
                    metadata.partition(),
                    metadata.offset(),
                    event.eventId(),
                    event.eventType(),
                    event.actionType(),
                    event.reason()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while publishing leaderboard event: eventId="
                            + event.eventId(),
                    e
            );
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Failed to publish leaderboard event: topic=" + topic
                            + ", eventId=" + event.eventId(),
                    e.getCause()
            );
        }
    }
}