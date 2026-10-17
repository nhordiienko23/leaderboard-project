package com.github.nhordiienko23.postservice.repository;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import com.github.nhordiienko23.postservice.kafka.KafkaTopics;
import com.github.nhordiienko23.postservice.testcontainers.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@DisplayName("Outbox Repository Integration Tests")
class OutboxRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    @DisplayName("Should return only unpublished entries ordered by ID")
    void shouldReturnUnpublishedEntriesInAscendingOrder() {
        OutboxEntry third = createEntry(3L, false);
        OutboxEntry first = createEntry(1L, false);
        OutboxEntry published = createEntry(2L, true);

        outboxRepository.save(third);
        outboxRepository.save(published);
        outboxRepository.save(first);
        outboxRepository.flush();

        var result =
                outboxRepository.findTop100ByPublishedFalseOrderByIdAsc();

        assertEquals(2, result.size());

        assertTrue(result.get(0).getId() < result.get(1).getId());
        assertFalse(result.get(0).isPublished());
        assertFalse(result.get(1).isPublished());

        assertEquals(3L, result.get(0).getContentId());
        assertEquals(1L, result.get(1).getContentId());
    }

    @Test
    @DisplayName("Should return an empty list when no unpublished entries exist")
    void shouldReturnEmptyListWhenNoUnpublishedEntriesExist() {
        outboxRepository.save(createEntry(1L, true));
        outboxRepository.flush();

        var result =
                outboxRepository.findTop100ByPublishedFalseOrderByIdAsc();

        assertTrue(result.isEmpty());
    }

    private OutboxEntry createEntry(Long contentId, boolean published) {
        return OutboxEntry.builder()
                .contentId(contentId)
                .actorId(23L)
                .eventType(EventType.POST)
                .actionType(ActionType.CREATED)
                .targetAuthorId(null)
                .contentAuthorId(23L)
                .topic(KafkaTopics.LEADERBOARD_EVENTS)
                .published(published)
                .attempts(0L)
                .lastError(null)
                .createdAt(LocalDateTime.now())
                .build();
    }
}