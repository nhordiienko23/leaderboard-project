package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeaderboardRedisInitializer implements CommandLineRunner {

    private static final String LEADERBOARD_KEY = "leaderboard";

    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(String... args) {
        redisTemplate.delete(LEADERBOARD_KEY);

        var entries = leaderboardEntryRepository.findAll();

        for (var entry : entries) {
            String userId = entry.getUserId().toString();

            redisTemplate.opsForZSet().add(
                    LEADERBOARD_KEY,
                    userId,
                    entry.getPoints().doubleValue()
            );

            redisTemplate.opsForHash().put(
                    "user:" + userId,
                    "username",
                    entry.getUsername()
            );
        }

        log.info("Redis leaderboard initialized with {} entries", entries.size());
    }
}