package com.github.nhordiienko23.leaderboardservice.kafka;

public record ContentEvent(
        Long eventId,
        Long userId,
        ActionType actionType

) {
}