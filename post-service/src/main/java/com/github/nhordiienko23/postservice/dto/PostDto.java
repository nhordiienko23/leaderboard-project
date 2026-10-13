package com.github.nhordiienko23.postservice.dto;

public record PostDto(
        Long id,
        String content,
        Long authorId
) {
}
