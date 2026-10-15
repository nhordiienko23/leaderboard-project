package com.github.nhordiienko23.postservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventProducer {
    private final KafkaTemplate<String, ContentEvent> kafkaTemplate;

    public void publish(String topic, ContentEvent event) {
        kafkaTemplate.send(topic,
                        event.eventId().toString(),
                        event
                )
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error("Failed to publish event: topic={}, eventId={},actionType={}",
                                topic,
                                event.eventId(),
                                event.actionType(),
                                exception);
                        return;
                    }
                    var metadata = result.getRecordMetadata();

                    log.info("Event published successfully: topic={}, partition={}, offset={}, eventId={}, actionType={}",
                            metadata.topic(),
                            metadata.partition(),
                            metadata.offset(),
                            event.eventId(),
                            event.actionType());
                });
    }
}
