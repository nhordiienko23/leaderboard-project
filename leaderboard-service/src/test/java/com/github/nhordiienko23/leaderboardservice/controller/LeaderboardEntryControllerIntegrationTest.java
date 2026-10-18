package com.github.nhordiienko23.leaderboardservice.controller;

import com.github.nhordiienko23.leaderboardservice.entity.LeaderboardEntry;
import com.github.nhordiienko23.leaderboardservice.repository.LeaderboardEntryRepository;
import com.github.nhordiienko23.leaderboardservice.service.LeaderboardRedisInitializer;
import com.github.nhordiienko23.leaderboardservice.testcontainers.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LeaderboardEntryControllerIntegrationTest
        extends AbstractIntegrationTest {

    private static final String LEADERBOARD_KEY = "leaderboard";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LeaderboardEntryRepository leaderboardEntryRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LeaderboardRedisInitializer redisInitializer;

    @BeforeEach
    void setUp() {
        leaderboardEntryRepository.deleteAll();

        redisTemplate.delete(LEADERBOARD_KEY);

        var userKeys = redisTemplate.keys("user:*");
        if (userKeys != null && !userKeys.isEmpty()) {
            redisTemplate.delete(userKeys);
        }
    }

    @Test
    void shouldReturnTop10EntriesSortedByPointsDescending()
            throws Exception {

        saveEntries(12);
        redisInitializer.run();

        mockMvc.perform(get("/api/leaderboard/top-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))

                .andExpect(jsonPath("$[0].userId").value(12))
                .andExpect(jsonPath("$[0].username").value("user-12"))
                .andExpect(jsonPath("$[0].points").value(120))

                .andExpect(jsonPath("$[9].userId").value(3))
                .andExpect(jsonPath("$[9].username").value("user-3"))
                .andExpect(jsonPath("$[9].points").value(30));
    }

    @Test
    void shouldReturnTop100EntriesSortedByPointsDescending()
            throws Exception {

        saveEntries(105);
        redisInitializer.run();

        mockMvc.perform(get("/api/leaderboard/top-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(100)))

                .andExpect(jsonPath("$[0].userId").value(105))
                .andExpect(jsonPath("$[0].username").value("user-105"))
                .andExpect(jsonPath("$[0].points").value(1050))

                .andExpect(jsonPath("$[99].userId").value(6))
                .andExpect(jsonPath("$[99].username").value("user-6"))
                .andExpect(jsonPath("$[99].points").value(60));
    }

    @Test
    void shouldReturnEmptyListWhenLeaderboardIsEmpty()
            throws Exception {

        mockMvc.perform(get("/api/leaderboard/top-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/leaderboard/top-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private void saveEntries(int count) {
        List<LeaderboardEntry> entries = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            entries.add(
                    LeaderboardEntry.builder()
                            .userId((long) i)
                            .username("user-" + i)
                            .points(i * 10L)
                            .build()
            );
        }

        leaderboardEntryRepository.saveAll(entries);
    }
}
