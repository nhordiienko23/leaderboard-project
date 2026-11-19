package com.github.nhordiienko23.userservice.kafka;

import com.github.nhordiienko23.leaderboardcontract.event.UserEvent;
import com.github.nhordiienko23.leaderboardcontract.event.UserEventType;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserEventProducer tests")
class UserEventProducerTest {

    @Mock
    private KafkaTemplate<String, UserEvent> kafkaTemplate;

    @InjectMocks
    private UserEventProducer sut;

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("publish: should send the event using the user ID as key")
        void publish_shouldSendEvent_whenBrokerSendSucceeds() {
            String topic = "user-events";
            UserEvent event = createEvent();
            SendResult<String, UserEvent> result = mock();
            RecordMetadata metadata = mock();
            CompletableFuture<SendResult<String, UserEvent>> future =
                    CompletableFuture.completedFuture(result);

            when(kafkaTemplate.send(topic, "42", event)).thenReturn(future);
            when(result.getRecordMetadata()).thenReturn(metadata);
            when(metadata.topic()).thenReturn(topic);
            when(metadata.partition()).thenReturn(1);
            when(metadata.offset()).thenReturn(10L);

            sut.publish(topic, event);

            verify(kafkaTemplate).send(topic, "42", event);
            verify(result).getRecordMetadata();
        }

        @Test
        @DisplayName("publish: should throw with the send cause when the broker send fails")
        void publish_shouldThrow_whenBrokerSendFails() throws Exception {
            String topic = "user-events";
            UserEvent event = createEvent();
            RuntimeException cause = new RuntimeException("broker unavailable");
            CompletableFuture<SendResult<String, UserEvent>> future = mock();

            when(kafkaTemplate.send(topic, "42", event)).thenReturn(future);
            when(future.get()).thenThrow(new ExecutionException(cause));

            assertThatThrownBy(() -> sut.publish(topic, event))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Failed to publish user event: eventId=1001")
                    .hasCause(cause);

            verify(kafkaTemplate).send(topic, "42", event);
            verify(future).get();
        }

        @Test
        @DisplayName("publish: should restore the interrupt flag when waiting is interrupted")
        void publish_shouldRestoreInterruptFlag_whenWaitingIsInterrupted() throws Exception {
            String topic = "user-events";
            UserEvent event = createEvent();
            InterruptedException cause = new InterruptedException("interrupted");
            CompletableFuture<SendResult<String, UserEvent>> future = mock();
            boolean wasInterrupted = Thread.interrupted();

            try {
                when(kafkaTemplate.send(topic, "42", event)).thenReturn(future);
                when(future.get()).thenThrow(cause);

                assertThatThrownBy(() -> sut.publish(topic, event))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Interrupted while publishing user event: eventId=1001")
                        .hasCause(cause);

                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                verify(kafkaTemplate).send(topic, "42", event);
                verify(future).get();
            } finally {
                Thread.interrupted();
                if (wasInterrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Test
        @DisplayName("publish: should throw before sending when the event is null")
        void publish_shouldThrowBeforeSending_whenEventIsNull() {
            assertThatThrownBy(() -> sut.publish("user-events", null))
                    .isInstanceOf(NullPointerException.class);

            verifyNoInteractions(kafkaTemplate);
        }
    }

    private UserEvent createEvent() {
        return new UserEvent(
                1001L,
                42L,
                "alice",
                UserEventType.CREATED,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}