package com.github.nhordiienko23.postservice.dto;

import java.time.LocalDateTime;

public record LikeDTO(
        Long id,
        Long userId,
        Long postId,
        Long commentId,
        LocalDateTime createdAt
) {
}