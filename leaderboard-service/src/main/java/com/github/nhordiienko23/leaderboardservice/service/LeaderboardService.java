package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardservice.dto.LeaderboardEntryDTO;
import com.github.nhordiienko23.leaderboardservice.kafka.PostCreatedEvent;

import java.util.List;

public interface LeaderboardService {
    void processPostCreated(PostCreatedEvent event);
    List<LeaderboardEntryDTO> getLeaderboard(int limit);
}
