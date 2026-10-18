package com.github.nhordiienko23.postservice.kafka;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.leaderboardcontract.event.LeaderboardEvent;
import com.github.nhordiienko23.leaderboardcontract.event.ParticipantRole;
import com.github.nhordiienko23.leaderboardcontract.event.ScoreParticipant;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostEventProducerTest {

    private static final String TOPIC = "leaderboard-events";
    private static final String EXPECTED_KEY = "POST:10";

    private static final LeaderboardEvent EVENT = new LeaderboardEvent(
            100L,
            EventType.POST,
            ActionType.CREATED,
            EventReason.USER_ACTION,
            10L,
            List.of(
                    new ScoreParticipant(
                            23L,
                            ParticipantRole.POST_AUTHOR
                    )
            )
    );

    @Mock
    private KafkaTemplate<String, LeaderboardEvent> kafkaTemplate;

    @Mock
    private SendResult<String, LeaderboardEvent> sendResult;

    @Mock
    private RecordMetadata recordMetadata;

    private PostEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new PostEventProducer(kafkaTemplate);
    }

    @AfterEach
    void clearInterruptStatus() {
        // Avoid leaking the interrupt flag into other tests.
        Thread.interrupted();
    }

    @Test
    void publish_shouldSendEventWithCorrectTopicKeyAndPayload()
            throws Exception {

        CompletableFuture<SendResult<String, LeaderboardEvent>> future =
                CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(TOPIC, EXPECTED_KEY, EVENT))
                .thenReturn(future);
        when(sendResult.getRecordMetadata())
                .thenReturn(recordMetadata);
        when(recordMetadata.topic()).thenReturn(TOPIC);
        when(recordMetadata.partition()).thenReturn(0);
        when(recordMetadata.offset()).thenReturn(15L);

        producer.publish(TOPIC, EVENT);

        verify(kafkaTemplate).send(TOPIC, EXPECTED_KEY, EVENT);
        verify(sendResult).getRecordMetadata();
        verify(recordMetadata).topic();
        verify(recordMetadata).partition();
        verify(recordMetadata).offset();
    }

    @Test
    void publish_shouldUseEventTypeAndEntityIdAsMessageKey()
            throws Exception {

        CompletableFuture<SendResult<String, LeaderboardEvent>> future =
                CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(TOPIC, EXPECTED_KEY, EVENT))
                .thenReturn(future);
        when(sendResult.getRecordMetadata())
                .thenReturn(recordMetadata);
        when(recordMetadata.topic()).thenReturn(TOPIC);
        when(recordMetadata.partition()).thenReturn(0);
        when(recordMetadata.offset()).thenReturn(15L);

        producer.publish(TOPIC, EVENT);

        ArgumentCaptor<String> keyCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate).send(
                eq(TOPIC),
                keyCaptor.capture(),
                eq(EVENT)
        );

        assertEquals(EXPECTED_KEY, keyCaptor.getValue());
    }

    @Test
    void publish_shouldThrowIllegalStateExceptionWhenKafkaFails()
            throws Exception {

        RuntimeException kafkaException =
                new RuntimeException("Kafka unavailable");

        CompletableFuture<SendResult<String, LeaderboardEvent>> future =
                mock(CompletableFuture.class);

        when(kafkaTemplate.send(TOPIC, EXPECTED_KEY, EVENT))
                .thenReturn(future);

        when(future.get()).thenThrow(
                new ExecutionException(kafkaException)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> producer.publish(TOPIC, EVENT)
        );

        assertEquals(
                "Failed to publish leaderboard event: topic="
                        + TOPIC + ", eventId=100",
                exception.getMessage()
        );
        assertSame(kafkaException, exception.getCause());

        verify(kafkaTemplate).send(TOPIC, EXPECTED_KEY, EVENT);
        verifyNoInteractions(sendResult, recordMetadata);
    }

    @Test
    void publish_shouldRestoreInterruptStatusWhenInterrupted()
            throws Exception {

        CompletableFuture<SendResult<String, LeaderboardEvent>> future =
                mock(CompletableFuture.class);

        when(kafkaTemplate.send(TOPIC, EXPECTED_KEY, EVENT))
                .thenReturn(future);

        when(future.get()).thenThrow(
                new InterruptedException("Publishing interrupted")
        );

        try {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> producer.publish(TOPIC, EVENT)
            );

            assertEquals(
                    "Interrupted while publishing leaderboard event: eventId=100",
                    exception.getMessage()
            );
            assertTrue(Thread.currentThread().isInterrupted());

            assertEquals(
                    InterruptedException.class,
                    exception.getCause().getClass()
            );

            verify(kafkaTemplate).send(TOPIC, EXPECTED_KEY, EVENT);
            verifyNoInteractions(sendResult, recordMetadata);
        } finally {
            Thread.interrupted();
        }
    }
}
