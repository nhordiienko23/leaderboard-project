package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.LeaderboardEvent;
import com.github.nhordiienko23.leaderboardservice.dto.LeaderboardEntryDTO;

import java.util.List;

public interface LeaderboardService {

    void processLeaderboardEvent(LeaderboardEvent event);

    List<LeaderboardEntryDTO> getLeaderboard(int limit);
}