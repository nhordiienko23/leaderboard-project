package com.github.nhordiienko23.postservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequest(

        @NotBlank(message = "Comment content must not be blank")
        @Size(max = 1000, message = "Comment content must not exceed 1000 characters")
        String content

) {
}