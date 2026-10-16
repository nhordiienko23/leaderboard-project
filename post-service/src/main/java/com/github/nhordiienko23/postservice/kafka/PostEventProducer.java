package com.github.nhordiienko23.postservice.kafka;

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

    private final KafkaTemplate<String, ContentEvent> kafkaTemplate;

    public void publish(String topic, ContentEvent event) {
        try {
            SendResult<String, ContentEvent> result = kafkaTemplate
                    .send(topic, event.eventId().toString(), event)
                    .get();

            var metadata = result.getRecordMetadata();

            log.info(
                    "Event published successfully: topic={}, partition={}, offset={}, eventId={}, actionType={}",
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset(),
                    event.eventId(),
                    event.actionType()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while publishing event", e
            );

        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Failed to publish event: topic=" + topic
                            + ", eventId=" + event.eventId(),
                    e.getCause()
            );
        }
    }
}