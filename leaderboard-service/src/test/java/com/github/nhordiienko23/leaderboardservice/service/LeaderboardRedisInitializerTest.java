package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardRedisInitializerTest {

    private static final String LEADERBOARD_KEY = "leaderboard";

    @Mock
    private LeaderboardEntryRepository leaderboardEntryRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private LeaderboardRedisInitializer leaderboardRedisInitializer;

    @Test
    void shouldClearRedisAndPopulateLeaderboardFromDatabase() {
        LeaderboardEntry firstEntry = leaderboardEntry(
                23L, "Nikita", 30L
        );
        LeaderboardEntry secondEntry = leaderboardEntry(
                2L, "Alex", 10L
        );

        when(leaderboardEntryRepository.findAll())
                .thenReturn(List.of(firstEntry, secondEntry));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        leaderboardRedisInitializer.run();

        InOrder inOrder = inOrder(
                redisTemplate,
                leaderboardEntryRepository
        );

        inOrder.verify(redisTemplate).delete(LEADERBOARD_KEY);
        inOrder.verify(leaderboardEntryRepository).findAll();

        verify(zSetOperations).add(
                LEADERBOARD_KEY,
                "23",
                30.0
        );
        verify(zSetOperations).add(
                LEADERBOARD_KEY,
                "2",
                10.0
        );

        verify(hashOperations).put(
                "user:23",
                "username",
                "Nikita"
        );
        verify(hashOperations).put(
                "user:2",
                "username",
                "Alex"
        );

        verify(zSetOperations, times(2))
                .add(
                        org.mockito.ArgumentMatchers.eq(LEADERBOARD_KEY),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyDouble()
                );

        verify(hashOperations, times(2))
                .put(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("username"),
                        org.mockito.ArgumentMatchers.anyString()
                );
    }

    @Test
    void shouldClearLeaderboardWhenDatabaseIsEmpty() {
        when(leaderboardEntryRepository.findAll())
                .thenReturn(List.of());

        leaderboardRedisInitializer.run();

        verify(redisTemplate).delete(LEADERBOARD_KEY);
        verify(leaderboardEntryRepository).findAll();

        verify(redisTemplate, never()).opsForZSet();
        verify(redisTemplate, never()).opsForHash();

        verifyNoInteractions(zSetOperations, hashOperations);
    }

    @Test
    void shouldStoreZeroScoreCorrectly() {
        LeaderboardEntry entry = leaderboardEntry(
                7L, "NewUser", 0L
        );

        when(leaderboardEntryRepository.findAll())
                .thenReturn(List.of(entry));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        leaderboardRedisInitializer.run();

        verify(zSetOperations).add(
                LEADERBOARD_KEY,
                "7",
                0.0
        );

        verify(hashOperations).put(
                "user:7",
                "username",
                "NewUser"
        );
    }

    private LeaderboardEntry leaderboardEntry(
            Long userId,
            String username,
            Long points
    ) {
        return LeaderboardEntry.builder()
                .userId(userId)
                .username(username)
                .points(points)
                .build();
    }
}
