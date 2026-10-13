package com.github.nhordiienko23.postservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PostCreationRequest(

        @NotBlank(message = "content cannot be empty")
        @Size(max = 255, message = "content must not exceed 255 characters")
        String content,

        @NotNull(message = "authorId cannot be null")
        @Positive(message = "authorId must be positive")
        Long authorId
) {
}