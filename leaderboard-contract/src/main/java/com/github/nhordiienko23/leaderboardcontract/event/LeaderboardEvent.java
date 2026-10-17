package com.github.nhordiienko23.leaderboardcontract.event;

import java.util.List;

public record LeaderboardEvent(
        Long eventId,
        EventType eventType,
        ActionType actionType,
        Long entityId,
        List<ScoreParticipant> participants
) {
    public LeaderboardEvent {
        participants = List.copyOf(participants);
    }
}
