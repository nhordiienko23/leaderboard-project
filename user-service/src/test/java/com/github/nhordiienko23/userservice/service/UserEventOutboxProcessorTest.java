package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.UserEvent;
import com.github.nhordiienko23.leaderboardcontract.event.UserEventType;
import com.github.nhordiienko23.userservice.entity.UserEventOutbox;
import com.github.nhordiienko23.userservice.kafka.KafkaTopics;
import com.github.nhordiienko23.userservice.kafka.UserEventProducer;
import com.github.nhordiienko23.userservice.mapper.UserEventOutboxMapper;
import com.github.nhordiienko23.userservice.repository.UserEventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("User event outbox processor tests")
class UserEventOutboxProcessorTest {

    @Mock
    private UserEventOutboxRepository outboxRepository;

    @Mock
    private UserEventProducer userEventProducer;

    @Mock
    private UserEventOutboxMapper outboxMapper;

    @Test
    @DisplayName("Should publish an event and mark its outbox entry as published")
    void processPendingEntriesPublishesAndMarksEntry() {
        UserEventOutbox entry = createEntry(1L);
        UserEvent event = new UserEvent(
                1L, 42L, "user", UserEventType.CREATED, Instant.now()
        );
        entry.setLastError("previous error");

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));
        when(outboxMapper.toEvent(entry)).thenReturn(event);

        Instant before = Instant.now();

        createProcessor().processPendingEntries();

        Instant after = Instant.now();

        verify(userEventProducer).publish(KafkaTopics.USER_EVENTS, event);
        verify(outboxRepository).saveAll(List.of(entry));

        assertTrue(entry.isPublished());
        assertNotNull(entry.getPublishedAt());
        assertFalse(entry.getPublishedAt().isBefore(before));
        assertFalse(entry.getPublishedAt().isAfter(after));
        assertNull(entry.getLastError());
        assertEquals(0L, entry.getAttempts());
    }

    @Test
    @DisplayName("Should save an empty batch without processing entries")
    void processPendingEntriesSavesEmptyBatch() {
        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of());

        createProcessor().processPendingEntries();

        verify(outboxRepository).saveAll(List.of());
        verifyNoInteractions(userEventProducer, outboxMapper);
    }

    @Test
    @DisplayName("Should record a publish failure and continue processing the batch")
    void processPendingEntriesRecordsFailureAndContinues() {
        UserEventOutbox failedEntry = createEntry(1L);
        UserEventOutbox nextEntry = createEntry(2L);
        nextEntry.setUserId(43L);
        nextEntry.setUsername("another-user");

        UserEvent failedEvent = new UserEvent(
                1L, 42L, "user", UserEventType.CREATED, Instant.now()
        );
        UserEvent nextEvent = new UserEvent(
                2L, 43L, "another-user", UserEventType.UPDATED, Instant.now()
        );

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(failedEntry, nextEntry));
        when(outboxMapper.toEvent(failedEntry)).thenReturn(failedEvent);
        when(outboxMapper.toEvent(nextEntry)).thenReturn(nextEvent);

        doThrow(new IllegalStateException("broker unavailable"))
                .when(userEventProducer)
                .publish(KafkaTopics.USER_EVENTS, failedEvent);

        createProcessor().processPendingEntries();

        verify(userEventProducer).publish(KafkaTopics.USER_EVENTS, failedEvent);
        verify(userEventProducer).publish(KafkaTopics.USER_EVENTS, nextEvent);
        verify(outboxRepository).saveAll(List.of(failedEntry, nextEntry));

        // Failed entry remains unpublished and records the failure.
        assertFalse(failedEntry.isPublished());
        assertNull(failedEntry.getPublishedAt());
        assertEquals(1L, failedEntry.getAttempts());
        assertEquals("broker unavailable", failedEntry.getLastError());

        // Processing continues and the next entry is published successfully.
        assertTrue(nextEntry.isPublished());
        assertNotNull(nextEntry.getPublishedAt());
        assertEquals(0L, nextEntry.getAttempts());
        assertNull(nextEntry.getLastError());
    }

    @Test
    @DisplayName("Should truncate a failure message to 2000 characters")
    void processPendingEntriesTruncatesLongFailureMessage() {
        UserEventOutbox entry = createEntry(1L);
        UserEvent event = new UserEvent(
                1L, 42L, "user", UserEventType.CREATED, Instant.now()
        );
        String longMessage = "x".repeat(2001);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));
        when(outboxMapper.toEvent(entry)).thenReturn(event);

        doThrow(new IllegalStateException(longMessage))
                .when(userEventProducer)
                .publish(KafkaTopics.USER_EVENTS, event);

        createProcessor().processPendingEntries();

        assertEquals(2000, entry.getLastError().length());
        assertEquals(longMessage.substring(0, 2000), entry.getLastError());
        assertEquals(1L, entry.getAttempts());
        assertFalse(entry.isPublished());
    }

    @Test
    @DisplayName("Should use the exception class name when a failure has no message")
    void processPendingEntriesUsesExceptionClassWhenMessageIsNull() {
        UserEventOutbox entry = createEntry(1L);
        UserEvent event = new UserEvent(
                1L, 42L, "user", UserEventType.CREATED, Instant.now()
        );

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));
        when(outboxMapper.toEvent(entry)).thenReturn(event);

        doThrow(new IllegalStateException())
                .when(userEventProducer)
                .publish(KafkaTopics.USER_EVENTS, event);

        createProcessor().processPendingEntries();

        assertEquals("IllegalStateException", entry.getLastError());
        assertEquals(1L, entry.getAttempts());
        assertFalse(entry.isPublished());
    }

    @Test
    @DisplayName("Should count a mapping failure as a failed processing attempt")
    void processPendingEntriesRecordsMappingFailure() {
        UserEventOutbox entry = createEntry(1L);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));

        doThrow(new IllegalArgumentException("mapping failed"))
                .when(outboxMapper)
                .toEvent(entry);

        createProcessor().processPendingEntries();

        verifyNoInteractions(userEventProducer);
        verify(outboxRepository).saveAll(List.of(entry));

        assertEquals(1L, entry.getAttempts());
        assertEquals("mapping failed", entry.getLastError());
        assertFalse(entry.isPublished());
    }

    private UserEventOutboxProcessor createProcessor() {
        return new UserEventOutboxProcessor(
                outboxRepository,
                userEventProducer,
                outboxMapper
        );
    }

    private UserEventOutbox createEntry(Long id) {
        return UserEventOutbox.builder()
                .id(id)
                .userId(42L)
                .username("user")
                .eventType(UserEventType.CREATED)
                .published(false)
                .attempts(0L)
                .occurredAt(Instant.now())
                .build();
    }
}
