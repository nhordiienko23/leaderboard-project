
package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LeaderboardRedisInitializer implements CommandLineRunner {

    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(String... args) {
        redisTemplate.delete("leaderboard");
        var entries = leaderboardEntryRepository.findAll();

        for (var entry : entries) {
            redisTemplate.opsForZSet().add(
                    "leaderboard",
                    entry.getAuthorId().toString(),
                    entry.getPoints().doubleValue()
            );
        }
    }
}
