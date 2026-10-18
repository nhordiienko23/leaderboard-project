package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventReason;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox Processor Tests")
class OutboxProcessorTest {

    private static final String PARTICIPANTS_JSON =
            "[{\"userId\":1,\"role\":\"COMMENT_AUTHOR\"},"
                    + "{\"userId\":23,\"role\":\"CONTENT_AUTHOR\"}]";

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private PostEventProducer postEventProducer;

    @Mock
    private JsonMapper jsonMapper;

    @InjectMocks
    private OutboxProcessor outboxProcessor;

    // -------------------------------------------------------------------------
    // SUCCESSFUL PROCESSING
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should publish pending post event successfully")
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
        assertEquals(101L, event.entityId());
        assertEquals(EventType.POST, event.eventType());
        assertEquals(ActionType.CREATED, event.actionType());
        assertEquals(EventReason.USER_ACTION, event.reason());

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
    @DisplayName("Should use configured event reason")
    void shouldPreserveConfiguredReason() {
        OutboxEntry entry = createOutboxEntry(1L);
        entry.setReason(EventReason.MODERATION);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));

        ArgumentCaptor<LeaderboardEvent> captor =
                ArgumentCaptor.forClass(LeaderboardEvent.class);

        outboxProcessor.processPendingEntries();

        verify(postEventProducer).publish(
                eq(KafkaTopics.LEADERBOARD_EVENTS),
                captor.capture()
        );

        assertEquals(
                EventReason.MODERATION,
                captor.getValue().reason()
        );
    }

    @Test
    @DisplayName("Should deserialize participants from JSON")
    void shouldDeserializeParticipantsFromJson() {
        OutboxEntry entry = createOutboxEntry(1L);
        entry.setEventType(EventType.COMMENT);
        entry.setActionType(ActionType.DELETED);
        entry.setReason(EventReason.PARENT_DELETED);
        entry.setParticipantsJson(PARTICIPANTS_JSON);

        List<ScoreParticipant> participants = List.of(
                new ScoreParticipant(
                        1L,
                        ParticipantRole.COMMENT_AUTHOR
                ),
                new ScoreParticipant(
                        23L,
                        ParticipantRole.CONTENT_AUTHOR
                )
        );

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));

        when(jsonMapper.readValue(
                eq(PARTICIPANTS_JSON),
                anyTypeReference()
        )).thenReturn(participants);

        ArgumentCaptor<LeaderboardEvent> captor =
                ArgumentCaptor.forClass(LeaderboardEvent.class);

        outboxProcessor.processPendingEntries();

        verify(postEventProducer).publish(
                eq(KafkaTopics.LEADERBOARD_EVENTS),
                captor.capture()
        );

        LeaderboardEvent event = captor.getValue();

        assertEquals(EventType.COMMENT, event.eventType());
        assertEquals(ActionType.DELETED, event.actionType());
        assertEquals(EventReason.PARENT_DELETED, event.reason());
        assertEquals(participants, event.participants());

        verify(jsonMapper).readValue(
                eq(PARTICIPANTS_JSON),
                anyTypeReference()
        );
    }

    // -------------------------------------------------------------------------
    // PUBLISHING FAILURES AND RETRIES
    // -------------------------------------------------------------------------

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

        // The attempts counter records previous failures.
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

        verify(outboxRepository).saveAll(
                List.of(first, second, third)
        );
    }

    // -------------------------------------------------------------------------
    // INVALID OUTBOX DATA
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should reject non-post event without participants JSON")
    void shouldRejectMissingParticipantsForNonPostEvent() {
        OutboxEntry entry = createOutboxEntry(1L);
        entry.setEventType(EventType.COMMENT);
        entry.setParticipantsJson(null);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));

        outboxProcessor.processPendingEntries();

        assertFalse(entry.isPublished());
        assertEquals(1L, entry.getAttempts());
        assertTrue(entry.getLastError().contains(
                "participantsJson is required"
        ));

        verifyNoInteractions(postEventProducer, jsonMapper);
        verify(outboxRepository).saveAll(List.of(entry));
    }

    @Test
    @DisplayName("Should handle invalid participants JSON")
    void shouldHandleInvalidParticipantsJson() {
        OutboxEntry entry = createOutboxEntry(1L);
        entry.setEventType(EventType.COMMENT);
        entry.setParticipantsJson(PARTICIPANTS_JSON);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));

        when(jsonMapper.readValue(
                eq(PARTICIPANTS_JSON),
                anyTypeReference()
        )).thenThrow(new IllegalStateException("Invalid JSON"));

        outboxProcessor.processPendingEntries();

        assertFalse(entry.isPublished());
        assertEquals(1L, entry.getAttempts());
        assertTrue(entry.getLastError().contains(
                "Cannot deserialize outbox participants"
        ));

        verifyNoInteractions(postEventProducer);
        verify(outboxRepository).saveAll(List.of(entry));
    }

    @Test
    @DisplayName("Should reject empty participants list")
    void shouldRejectEmptyParticipants() {
        OutboxEntry entry = createOutboxEntry(1L);
        entry.setEventType(EventType.COMMENT);
        entry.setParticipantsJson("[]");

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));

        when(jsonMapper.readValue(
                eq("[]"),
                anyTypeReference()
        )).thenReturn(List.of());

        outboxProcessor.processPendingEntries();

        assertFalse(entry.isPublished());
        assertEquals(1L, entry.getAttempts());
        assertTrue(entry.getLastError().contains(
                "Outbox event must contain participants"
        ));

        verifyNoInteractions(postEventProducer);
        verify(outboxRepository).saveAll(List.of(entry));
    }

    @Test
    @DisplayName("Should reject outbox entry without ID")
    void shouldRejectEntryWithoutId() {
        OutboxEntry entry = createOutboxEntry(null);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));

        outboxProcessor.processPendingEntries();

        assertFalse(entry.isPublished());
        assertEquals(1L, entry.getAttempts());
        assertEquals("Outbox ID must not be null", entry.getLastError());

        verifyNoInteractions(postEventProducer, jsonMapper);
        verify(outboxRepository).saveAll(List.of(entry));
    }

    // -------------------------------------------------------------------------
    // EMPTY QUEUE
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should handle an empty outbox queue")
    void shouldHandleEmptyQueue() {
        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of());

        outboxProcessor.processPendingEntries();

        verify(outboxRepository)
                .findTop100ByPublishedFalseOrderByIdAsc();
        verify(outboxRepository).saveAll(List.of());
        verifyNoInteractions(postEventProducer, jsonMapper);
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private TypeReference<List<ScoreParticipant>> anyTypeReference() {
        return any(TypeReference.class);
    }

    private OutboxEntry createOutboxEntry(Long id) {
        return OutboxEntry.builder()
                .id(id)
                .contentId(id == null ? 101L : 100L + id)
                .eventType(EventType.POST)
                .actionType(ActionType.CREATED)
                .reason(null)
                .topic(KafkaTopics.LEADERBOARD_EVENTS)
                .actorId(23L)
                .targetAuthorId(null)
                .contentAuthorId(23L)
                .participantsJson(null)
                .published(false)
                .publishedAt(null)
                .lastError(null)
                .createdAt(LocalDateTime.now())
                .attempts(0L)
                .build();
    }
}