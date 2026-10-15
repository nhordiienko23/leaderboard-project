package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardservice.dto.LeaderboardEntryDTO;
import com.github.nhordiienko23.leaderboardservice.kafka.ContentEvent;

import java.util.List;

public interface LeaderboardService {
    void processPostEvent(ContentEvent event);
    List<LeaderboardEntryDTO> getLeaderboard(int limit);
}
