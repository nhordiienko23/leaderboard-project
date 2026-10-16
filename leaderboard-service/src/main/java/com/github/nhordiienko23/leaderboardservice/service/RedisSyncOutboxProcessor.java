package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardservice.entity.RedisSyncOutbox;
import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import com.github.nhordiienko23.leaderboardservice.repository.RedisSyncOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisSyncOutboxProcessor {

    private final RedisSyncOutboxRepository redisSyncOutboxRepository;
    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 1000)
    public void processPendingTasks() {
        var tasks = redisSyncOutboxRepository
                .findTop100ByProcessedFalseOrderByIdAsc();

        Map<Long, List<RedisSyncOutbox>> tasksByUser = tasks.stream()
                .collect(Collectors.groupingBy(RedisSyncOutbox::getUserId));

        for (var userTasks : tasksByUser.entrySet()) {
            Long userId = userTasks.getKey();
            List<RedisSyncOutbox> outboxTasks = userTasks.getValue();

            try {
                var entry = leaderboardEntryRepository.findByUserId(userId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Leaderboard entry not found for userId: " + userId
                        ));

                redisTemplate.opsForZSet().add(
                        "leaderboard",
                        userId.toString(),
                        entry.getPoints()
                );

                redisTemplate.opsForHash().put(
                        "user:" + userId,
                        "username",
                        entry.getUsername()
                );

                LocalDateTime now = LocalDateTime.now();

                for (RedisSyncOutbox task : outboxTasks) {
                    task.setProcessed(true);
                    task.setProcessedAt(now);
                    task.setLastError(null);
                }

                log.debug("Redis sync completed for userId={}", userId);

            } catch (Exception e) {
                for (RedisSyncOutbox task : outboxTasks) {
                    task.setAttempts(task.getAttempts() + 1);
                    task.setLastError(e.getMessage());
                }

                log.error("Redis sync failed for userId={}", userId, e);
            }
        }

        redisSyncOutboxRepository.saveAll(tasks);
    }
}
