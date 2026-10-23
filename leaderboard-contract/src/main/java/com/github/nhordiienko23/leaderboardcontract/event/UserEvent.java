package com.github.nhordiienko23.leaderboardcontract.event;

import java.time.Instant;

public record UserEvent(
        Long eventId,
        Long userId,
        String username,
        UserEventType actionType,
        Instant occurredAt
) {
}