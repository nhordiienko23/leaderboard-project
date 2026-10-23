package com.github.nhordiienko23.userservice.kafka;

import com.github.nhordiienko23.leaderboardcontract.event.UserEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventProducer {

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;

    public void publish(String topic, UserEvent event) {
        String key = event.userId().toString();

        try {
            SendResult<String, UserEvent> result = kafkaTemplate
                    .send(topic, key, event)
                    .get();

            var metadata = result.getRecordMetadata();

            log.info(
                    "User event published: topic={}, key={}, partition={}, offset={}, eventId={}, userId={}, eventType={}",
                    metadata.topic(),
                    key,
                    metadata.partition(),
                    metadata.offset(),
                    event.eventId(),
                    event.userId(),
                    event.actionType()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while publishing user event: eventId="
                            + event.eventId(),
                    e
            );

        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Failed to publish user event: eventId="
                            + event.eventId(),
                    e.getCause()
            );
        }
    }
}