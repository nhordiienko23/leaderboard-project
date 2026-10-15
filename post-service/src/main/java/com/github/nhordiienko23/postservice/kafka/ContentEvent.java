package com.github.nhordiienko23.postservice.kafka;

public record ContentEvent(
        Long eventId,
        Long userId,
        ActionType actionType
) {
}