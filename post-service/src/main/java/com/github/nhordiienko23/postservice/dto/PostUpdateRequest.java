package com.github.nhordiienko23.postservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostUpdateRequest(
        @NotBlank(message = "content cannot be empty")
        @Size(max = 255, message = "content must not exceed 255 characters")
        String content
) {
}
