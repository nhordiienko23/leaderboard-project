package com.github.nhordiienko23.postservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventProducer {
    private static final String TOPIC = "post-created";
    private final KafkaTemplate<String, PostCreatedEvent> kafkaTemplate;

    public void sendPostCreatedEvent(PostCreatedEvent event) {
        kafkaTemplate.send(TOPIC, event)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error("Failed to send PostCreatedEvent for postId={}",
                                event.postId(),
                                exception);
                        return;
                    }
                    var metadata = result.getRecordMetadata();

                    log.info("PostCreatedEvent sent successfully: topic={}, partition={}, offset={}, postId={}",
                            metadata.topic(),
                            metadata.partition(),
                            metadata.offset(),
                            event.postId());
                });
    }
}
