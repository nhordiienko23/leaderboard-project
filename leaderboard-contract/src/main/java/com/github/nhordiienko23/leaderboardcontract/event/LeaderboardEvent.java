package com.github.nhordiienko23.leaderboardcontract.event;

import java.util.List;

public record LeaderboardEvent(
        Long eventId,
        EventType eventType,
        ActionType actionType,
        EventReason reason,
        Long entityId,
        List<ScoreParticipant> participants
) {
    public LeaderboardEvent {
        reason = reason == null ? EventReason.USER_ACTION : reason;
        participants = List.copyOf(participants);
    }

    /**
     * Backward-compatible constructor for existing tests and callers.
     */
    public LeaderboardEvent(
            Long eventId,
            EventType eventType,
            ActionType actionType,
            Long entityId,
            List<ScoreParticipant> participants
    ) {
        this(
                eventId,
                eventType,
                actionType,
                EventReason.USER_ACTION,
                entityId,
                participants
        );
    }
}