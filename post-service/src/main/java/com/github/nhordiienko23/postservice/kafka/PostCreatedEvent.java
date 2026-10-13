package com.github.nhordiienko23.postservice.kafka;

public record PostCreatedEvent(
        Long postId,
        Long authorId
) {
}