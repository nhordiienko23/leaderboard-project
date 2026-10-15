package com.github.nhordiienko23.postservice.dto;

import java.time.LocalDateTime;

public record PostDTO(
        Long id,
        String content,
        Long userId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
