package com.github.nhordiienko23.postservice.kafka;

import lombok.Builder;

@Builder
public record ContentEvent(
        Long eventId,
        Long userId,
        ActionType actionType
) {
}