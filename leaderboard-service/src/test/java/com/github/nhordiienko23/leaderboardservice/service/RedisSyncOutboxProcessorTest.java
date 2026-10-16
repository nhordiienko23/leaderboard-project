package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import com.github.nhordiienko23.leaderboardservice.entity.RedisSyncOutbox;
import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import com.github.nhordiienko23.leaderboardservice.repository.RedisSyncOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis Sync Outbox Processor tests")
class RedisSyncOutboxProcessorTest {

    @Mock
    private RedisSyncOutboxRepository outboxRepository;

    @Mock
    private LeaderboardEntryRepository leaderboardRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private RedisSyncOutboxProcessor processor;

    @Test
    @DisplayName("Should mark task as processed when Redis sync succeeds")
    void shouldMarkTaskAsProcessedWhenRedisSyncSucceeds() {

        RedisSyncOutbox task = createTask();
        LeaderboardEntry entry = createEntry();

        when(outboxRepository.findTop100ByProcessedFalseOrderByIdAsc())
                .thenReturn(List.of(task));

        when(leaderboardRepository.findByUserId(10L))
                .thenReturn(Optional.of(entry));

        when(redisTemplate.opsForZSet())
                .thenReturn(zSetOperations);

        when(redisTemplate.opsForHash())
                .thenReturn(hashOperations);


        processor.processPendingTasks();


        assertAll(
                () -> assertTrue(task.isProcessed()),
                () -> assertNotNull(task.getProcessedAt()),
                () -> assertNull(task.getLastError()),
                () -> assertEquals(0, task.getAttempts())
        );

        verify(zSetOperations).add("leaderboard", "10", 50.0);
        verify(hashOperations).put("user:10", "username", "Nikita");
        verify(outboxRepository).saveAll(List.of(task));
    }

    @Test
    @DisplayName("Should increase attempts when Redis ranking update fails")
    void shouldIncreaseAttemptsWhenRedisSyncFails() {

        RedisSyncOutbox task = createTask();
        LeaderboardEntry entry = createEntry();

        when(outboxRepository.findTop100ByProcessedFalseOrderByIdAsc())
                .thenReturn(List.of(task));

        when(leaderboardRepository.findByUserId(10L))
                .thenReturn(Optional.of(entry));

        when(redisTemplate.opsForZSet())
                .thenReturn(zSetOperations);

        when(zSetOperations.add("leaderboard", "10", 50.0))
                .thenThrow(new RuntimeException("Redis unavailable"));


        processor.processPendingTasks();


        assertAll(
                () -> assertFalse(task.isProcessed()),
                () -> assertEquals(1, task.getAttempts()),
                () -> assertEquals("Redis unavailable", task.getLastError()),
                () -> assertNull(task.getProcessedAt())
        );

        verify(zSetOperations).add("leaderboard", "10", 50.0);
        verify(outboxRepository).saveAll(List.of(task));
    }

    @Test
    @DisplayName("Should process task successfully after a failed attempt")
    void shouldProcessTaskOnRetryAfterRedisFailure() {

        RedisSyncOutbox task = createTask();
        LeaderboardEntry entry = createEntry();

        when(outboxRepository.findTop100ByProcessedFalseOrderByIdAsc())
                .thenReturn(List.of(task));

        when(leaderboardRepository.findByUserId(10L))
                .thenReturn(Optional.of(entry));

        when(redisTemplate.opsForZSet())
                .thenReturn(zSetOperations);

        when(redisTemplate.opsForHash())
                .thenReturn(hashOperations);

        when(zSetOperations.add("leaderboard", "10", 50.0))
                .thenThrow(new RuntimeException("Redis unavailable"))
                .thenReturn(true);


        processor.processPendingTasks();


        assertFalse(task.isProcessed());
        assertEquals(1, task.getAttempts());
        assertEquals("Redis unavailable", task.getLastError());


        processor.processPendingTasks();


        assertAll(
                () -> assertTrue(task.isProcessed()),
                () -> assertEquals(1, task.getAttempts()),
                () -> assertNull(task.getLastError()),
                () -> assertNotNull(task.getProcessedAt())
        );

        verify(zSetOperations, times(2))
                .add("leaderboard", "10", 50.0);

        verify(hashOperations)
                .put("user:10", "username", "Nikita");

        verify(outboxRepository, times(2))
                .saveAll(List.of(task));
    }

    @Test
    @DisplayName("Should keep task pending when Redis username update fails")
    void shouldIncreaseAttemptsWhenRedisUsernameUpdateFails() {

        RedisSyncOutbox task = createTask();
        LeaderboardEntry entry = createEntry();

        when(outboxRepository.findTop100ByProcessedFalseOrderByIdAsc())
                .thenReturn(List.of(task));

        when(leaderboardRepository.findByUserId(10L))
                .thenReturn(Optional.of(entry));

        when(redisTemplate.opsForZSet())
                .thenReturn(zSetOperations);

        when(redisTemplate.opsForHash())
                .thenReturn(hashOperations);

        doThrow(new RuntimeException("Redis hash unavailable"))
                .when(hashOperations)
                .put("user:10", "username", "Nikita");


        processor.processPendingTasks();


        assertAll(
                () -> assertFalse(task.isProcessed()),
                () -> assertEquals(1, task.getAttempts()),
                () -> assertEquals(
                        "Redis hash unavailable",
                        task.getLastError()
                ),
                () -> assertNull(task.getProcessedAt())
        );

        verify(zSetOperations).add("leaderboard", "10", 50.0);
        verify(hashOperations).put("user:10", "username", "Nikita");
        verify(outboxRepository).saveAll(List.of(task));
    }

    @Test
    @DisplayName("Should keep task pending when leaderboard entry does not exist")
    void shouldKeepTaskPendingWhenLeaderboardEntryDoesNotExist() {

        RedisSyncOutbox task = createTask();

        when(outboxRepository.findTop100ByProcessedFalseOrderByIdAsc())
                .thenReturn(List.of(task));

        when(leaderboardRepository.findByUserId(10L))
                .thenReturn(Optional.empty());


        processor.processPendingTasks();


        assertAll(
                () -> assertFalse(task.isProcessed()),
                () -> assertEquals(1, task.getAttempts()),
                () -> assertEquals(
                        "Leaderboard entry not found for userId: 10",
                        task.getLastError()
                ),
                () -> assertNull(task.getProcessedAt())
        );

        verifyNoInteractions(redisTemplate, zSetOperations, hashOperations);
        verify(outboxRepository).saveAll(List.of(task));
    }

    @Test
    @DisplayName("Should update Redis only once for multiple tasks of the same user")
    void shouldSyncRedisOnlyOnceForMultipleTasksOfSameUser() {

        RedisSyncOutbox firstTask = createTask(1L);
        RedisSyncOutbox secondTask = createTask(2L);
        LeaderboardEntry entry = createEntry();

        List<RedisSyncOutbox> tasks = List.of(firstTask, secondTask);

        when(outboxRepository.findTop100ByProcessedFalseOrderByIdAsc())
                .thenReturn(tasks);

        when(leaderboardRepository.findByUserId(10L))
                .thenReturn(Optional.of(entry));

        when(redisTemplate.opsForZSet())
                .thenReturn(zSetOperations);

        when(redisTemplate.opsForHash())
                .thenReturn(hashOperations);


        processor.processPendingTasks();


        assertAll(
                () -> assertTrue(firstTask.isProcessed()),
                () -> assertTrue(secondTask.isProcessed()),
                () -> assertNotNull(firstTask.getProcessedAt()),
                () -> assertNotNull(secondTask.getProcessedAt()),
                () -> assertEquals(0, firstTask.getAttempts()),
                () -> assertEquals(0, secondTask.getAttempts())
        );

        verify(leaderboardRepository).findByUserId(10L);

        verify(zSetOperations)
                .add("leaderboard", "10", 50.0);

        verify(hashOperations)
                .put("user:10", "username", "Nikita");

        verify(outboxRepository).saveAll(tasks);
    }

    @Test
    @DisplayName("Should not access Redis when there are no pending tasks")
    void shouldNotAccessRedisWhenThereAreNoPendingTasks() {

        when(outboxRepository.findTop100ByProcessedFalseOrderByIdAsc())
                .thenReturn(List.of());


        processor.processPendingTasks();


        verify(outboxRepository)
                .findTop100ByProcessedFalseOrderByIdAsc();

        verifyNoInteractions(
                leaderboardRepository,
                redisTemplate,
                zSetOperations,
                hashOperations
        );
    }

    private RedisSyncOutbox createTask() {
        return createTask(1L);
    }

    private RedisSyncOutbox createTask(Long id) {
        return RedisSyncOutbox.builder()
                .id(id)
                .userId(10L)
                .processed(false)
                .attempts(0)
                .build();
    }

    private LeaderboardEntry createEntry() {
        return LeaderboardEntry.builder()
                .userId(10L)
                .username("Nikita")
                .points(50L)
                .build();
    }
}
