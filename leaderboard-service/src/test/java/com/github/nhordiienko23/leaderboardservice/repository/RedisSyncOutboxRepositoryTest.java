package com.github.nhordiienko23.leaderboardservice.repository;

import com.github.nhordiienko23.leaderboardservice.entity.RedisSyncOutbox;
import com.github.nhordiienko23.leaderboardservice.testcontainers.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@DisplayName("Redis Sync Outbox Repository tests")
class RedisSyncOutboxRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private RedisSyncOutboxRepository repository;

    @Test
    @DisplayName("Should return pending tasks ordered by ID and limited to 100")
    void shouldReturnPendingTasksOrderedByIdAndLimitedTo100() {
        // Arrange
        List<RedisSyncOutbox> savedTasks = new ArrayList<>();

        for (int i = 1; i <= 150; i++) {
            RedisSyncOutbox task = RedisSyncOutbox.builder()
                    .userId((long) i)
                    .processed(i % 5 == 0)
                    .attempts(0)
                    .build();

            savedTasks.add(repository.save(task));
        }

        List<Long> expectedIds = savedTasks.stream()
                .filter(task -> !task.isProcessed())
                .limit(100)
                .map(RedisSyncOutbox::getId)
                .toList();

        // Act
        List<RedisSyncOutbox> actual =
                repository.findTop100ByProcessedFalseOrderByIdAsc();

        // Assert
        assertEquals(100, actual.size());

        assertEquals(
                expectedIds,
                actual.stream()
                        .map(RedisSyncOutbox::getId)
                        .toList()
        );

        assertTrue(actual.stream().noneMatch(RedisSyncOutbox::isProcessed));
    }

    @Test
    @DisplayName("Should set createdAt when a new task is saved")
    void shouldSetCreatedAtWhenSavingTask() {
        // Arrange
        RedisSyncOutbox task = RedisSyncOutbox.builder()
                .userId(10L)
                .processed(false)
                .attempts(0)
                .build();

        // Act
        RedisSyncOutbox saved = repository.saveAndFlush(task);

        // Assert
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    @DisplayName("Should return an empty list when no tasks exist")
    void shouldReturnEmptyListWhenNoTasksExist() {
        // Act
        List<RedisSyncOutbox> result =
                repository.findTop100ByProcessedFalseOrderByIdAsc();

        // Assert
        assertTrue(result.isEmpty());
    }
}
