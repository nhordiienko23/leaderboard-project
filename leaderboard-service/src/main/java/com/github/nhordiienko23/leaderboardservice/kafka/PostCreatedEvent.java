package com.github.nhordiienko23.leaderboardservice.kafka;

public record PostCreatedEvent(
        Long postId,
        Long authorId
) {
}