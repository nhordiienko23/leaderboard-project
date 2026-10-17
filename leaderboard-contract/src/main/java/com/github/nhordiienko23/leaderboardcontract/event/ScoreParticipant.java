package com.github.nhordiienko23.leaderboardcontract.event;

public record ScoreParticipant(
        Long userId,
        ParticipantRole role
) {
}
