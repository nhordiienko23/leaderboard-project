package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.kafka.ActionType;
import com.github.nhordiienko23.postservice.kafka.ContentEvent;
import com.github.nhordiienko23.postservice.kafka.EventType;
import com.github.nhordiienko23.postservice.kafka.KafkaTopics;
import com.github.nhordiienko23.postservice.kafka.PostEventProducer;
import com.github.nhordiienko23.postservice.mapper.OutboxEntryMapper;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox Processor Tests")
class OutboxProcessorTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private PostEventProducer postEventProducer;

    @Mock
    private OutboxEntryMapper outboxEntryMapper;

    @InjectMocks
    private OutboxProcessor outboxProcessor;

    @Test
    @DisplayName("Should publish pending entries successfully")
    void shouldProcessPendingEntries() {
        OutboxEntry entry = createOutboxEntry(1L);
        ContentEvent event = createContentEvent(1L);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));
        when(outboxEntryMapper.toContentEvent(entry)).thenReturn(event);

        outboxProcessor.processPendingEntries();

        assertTrue(entry.isPublished());
        assertNotNull(entry.getPublishedAt());
        assertNull(entry.getLastError());
        assertEquals(0L, entry.getAttempts());

        verify(postEventProducer).publish(KafkaTopics.POST_EVENTS, event);
        verify(outboxRepository).saveAll(List.of(entry));
    }

    @Test
    @DisplayName("Should increment attempts when publishing fails")
    void shouldIncrementAttemptsWhenPublishingFails() {
        OutboxEntry entry = createOutboxEntry(1L);
        ContentEvent event = createContentEvent(1L);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));
        when(outboxEntryMapper.toContentEvent(entry)).thenReturn(event);

        doThrow(new IllegalStateException("Kafka unavailable"))
                .when(postEventProducer)
                .publish(KafkaTopics.POST_EVENTS, event);

        outboxProcessor.processPendingEntries();

        assertFalse(entry.isPublished());
        assertNull(entry.getPublishedAt());
        assertEquals("Kafka unavailable", entry.getLastError());
        assertEquals(1L, entry.getAttempts());

        verify(postEventProducer).publish(KafkaTopics.POST_EVENTS, event);
        verify(outboxRepository).saveAll(List.of(entry));
    }

    @Test
    @DisplayName("Should publish an entry successfully after a failed attempt")
    void shouldProcessAfterFail() {
        OutboxEntry entry = createOutboxEntry(1L);
        ContentEvent event = createContentEvent(1L);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(entry));
        when(outboxEntryMapper.toContentEvent(entry)).thenReturn(event);

        doAnswer(invocation -> {
            throw new IllegalStateException("Kafka unavailable");
        }).doNothing()
                .when(postEventProducer)
                .publish(KafkaTopics.POST_EVENTS, event);

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

        verify(postEventProducer, times(2))
                .publish(KafkaTopics.POST_EVENTS, event);
        verify(outboxRepository, times(2))
                .saveAll(List.of(entry));
    }

    @Test
    @DisplayName("Should continue processing when one event fails")
    void shouldContinueProcessingWhenOneEventFails() {
        OutboxEntry first = createOutboxEntry(1L);
        OutboxEntry second = createOutboxEntry(2L);
        OutboxEntry third = createOutboxEntry(3L);

        ContentEvent firstEvent = createContentEvent(1L);
        ContentEvent secondEvent = createContentEvent(2L);
        ContentEvent thirdEvent = createContentEvent(3L);

        when(outboxRepository.findTop100ByPublishedFalseOrderByIdAsc())
                .thenReturn(List.of(first, second, third));

        when(outboxEntryMapper.toContentEvent(first)).thenReturn(firstEvent);
        when(outboxEntryMapper.toContentEvent(second)).thenReturn(secondEvent);
        when(outboxEntryMapper.toContentEvent(third)).thenReturn(thirdEvent);

        // Fail only when the second event is published.
        doAnswer(invocation -> {
            ContentEvent event = invocation.getArgument(1);

            if (event.eventId().equals(2L)) {
                throw new IllegalStateException("Kafka unavailable");
            }

            return null;
        }).when(postEventProducer)
                .publish(eq(KafkaTopics.POST_EVENTS), any(ContentEvent.class));

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

        verify(postEventProducer).publish(KafkaTopics.POST_EVENTS, firstEvent);
        verify(postEventProducer).publish(KafkaTopics.POST_EVENTS, secondEvent);
        verify(postEventProducer).publish(KafkaTopics.POST_EVENTS, thirdEvent);

        verify(outboxRepository).saveAll(List.of(first, second, third));
    }

    private ContentEvent createContentEvent(Long eventId) {
        return ContentEvent.builder()
                .eventId(eventId)
                .userId(23L)
                .actionType(ActionType.CREATED)
                .build();
    }

    private OutboxEntry createOutboxEntry(Long eventId) {
        return OutboxEntry.builder()
                .id(eventId)
                .eventId(eventId)
                .userId(23L)
                .published(false)
                .lastError(null)
                .topic(KafkaTopics.POST_EVENTS)
                .eventType(EventType.POST)
                .createdAt(LocalDateTime.now())
                .attempts(0L)
                .build();
    }
}
