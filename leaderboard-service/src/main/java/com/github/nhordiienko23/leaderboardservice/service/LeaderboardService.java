package com.github.nhordiienko23.leaderboardservice.service;

import com.github.nhordiienko23.leaderboardservice.kafka.PostCreatedEvent;

public interface LeaderboardService {
    void processPostCreated(PostCreatedEvent event);
}
