package com.github.nhordiienko23.postservice.dto;

import java.time.LocalDateTime;

public record CommentDTO(
        Long id,
        String content,
        Long userId,
        Long postId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}