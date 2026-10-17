package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.leaderboardcontract.event.LeaderboardEvent;
import com.github.nhordiienko23.leaderboardcontract.event.ParticipantRole;
import com.github.nhordiienko23.leaderboardcontract.event.ScoreParticipant;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.kafka.KafkaTopics;
import com.github.nhordiienko23.postservice.kafka.PostEventProducer;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox Processor Tests")
class OutboxProcessorTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private PostEventProducer postEventProducer;

    @InjectMocks
    private OutboxProcessor outboxProcessor;

    @Test
    @DisplayName("Should publish pending entries successfully")
    void shouldProcessPendingEntries() {
        OutboxEntry entry = createOutboxEntry(1L);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));

        outboxProcessor.processPendingEntries();

        assertTrue(entry.isPublished());
        assertNotNull(entry.getPublishedAt());
        assertNull(entry.getLastError());
        assertEquals(0L, entry.getAttempts());

        ArgumentCaptor<LeaderboardEvent> eventCaptor =
                ArgumentCaptor.forClass(LeaderboardEvent.class);

        verify(postEventProducer).publish(
                eq(KafkaTopics.LEADERBOARD_EVENTS),
                eventCaptor.capture()
        );

        LeaderboardEvent event = eventCaptor.getValue();

        assertEquals(1L, event.eventId());
        assertEquals(EventType.POST, event.eventType());
        assertEquals(ActionType.CREATED, event.actionType());
        assertEquals(101L, event.entityId());

        assertEquals(
                List.of(new ScoreParticipant(
                        23L,
                        ParticipantRole.POST_AUTHOR
                )),
                event.participants()
        );

        verify(outboxRepository).saveAll(List.of(entry));
    }

    @Test
    @DisplayName("Should increment attempts when publishing fails")
    void shouldIncrementAttemptsWhenPublishingFails() {
        OutboxEntry entry = createOutboxEntry(1L);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));

        doThrow(new IllegalStateException("Kafka unavailable"))
                .when(postEventProducer)
                .publish(
                        eq(KafkaTopics.LEADERBOARD_EVENTS),
                        any(LeaderboardEvent.class)
                );

        outboxProcessor.processPendingEntries();

        assertFalse(entry.isPublished());
        assertNull(entry.getPublishedAt());
        assertEquals("Kafka unavailable", entry.getLastError());
        assertEquals(1L, entry.getAttempts());

        verify(postEventProducer).publish(
                eq(KafkaTopics.LEADERBOARD_EVENTS),
                any(LeaderboardEvent.class)
        );

        verify(outboxRepository).saveAll(List.of(entry));
    }

    @Test
    @DisplayName("Should publish successfully after a failed attempt")
    void shouldProcessAfterFail() {
        OutboxEntry entry = createOutboxEntry(1L);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));

        doThrow(new IllegalStateException("Kafka unavailable"))
                .doNothing()
                .when(postEventProducer)
                .publish(
                        eq(KafkaTopics.LEADERBOARD_EVENTS),
                        any(LeaderboardEvent.class)
                );

        // First attempt fails.
        outboxProcessor.processPendingEntries();

        assertFalse(entry.isPublished());
        assertEquals(1L, entry.getAttempts());
        assertEquals("Kafka unavailable", entry.getLastError());

        // Second attempt succeeds.
        outboxProcessor.processPendingEntries();

        assertTrue(entry.isPublished());
        assertNotNull(entry.getPublishedAt());
        assertNull(entry.getLastError());
        assertEquals(1L, entry.getAttempts());

        verify(postEventProducer, times(2)).publish(
                eq(KafkaTopics.LEADERBOARD_EVENTS),
                any(LeaderboardEvent.class)
        );

        verify(outboxRepository, times(2))
                .saveAll(List.of(entry));
    }

    @Test
    @DisplayName("Should continue processing when one event fails")
    void shouldContinueProcessingWhenOneEventFails() {
        OutboxEntry first = createOutboxEntry(1L);
        OutboxEntry second = createOutboxEntry(2L);
        OutboxEntry third = createOutboxEntry(3L);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(first, second, third));

        doAnswer(invocation -> {
            LeaderboardEvent event = invocation.getArgument(1);

            if (event.eventId().equals(2L)) {
                throw new IllegalStateException("Kafka unavailable");
            }

            return null;
        }).when(postEventProducer).publish(
                eq(KafkaTopics.LEADERBOARD_EVENTS),
                any(LeaderboardEvent.class)
        );

        outboxProcessor.processPendingEntries();

        assertTrue(first.isPublished());
        assertFalse(second.isPublished());
        assertTrue(third.isPublished());

        assertEquals(0L, first.getAttempts());
        assertEquals(1L, second.getAttempts());
        assertEquals(0L, third.getAttempts());

        assertNull(first.getLastError());
        assertEquals("Kafka unavailable", second.getLastError());
        assertNull(third.getLastError());

        verify(postEventProducer, times(3)).publish(
                eq(KafkaTopics.LEADERBOARD_EVENTS),
                any(LeaderboardEvent.class)
        );

        verify(outboxRepository)
                .saveAll(List.of(first, second, third));
    }

    private OutboxEntry createOutboxEntry(Long id) {
        return OutboxEntry.builder()
                .id(id)
                .contentId(100L + id)
                .eventType(EventType.POST)
                .actionType(ActionType.CREATED)
                .topic(KafkaTopics.LEADERBOARD_EVENTS)
                .actorId(23L)
                .targetAuthorId(null)
                .contentAuthorId(23L)
                .published(false)
                .lastError(null)
                .createdAt(LocalDateTime.now())
                .attempts(0L)
                .build();
    }
}