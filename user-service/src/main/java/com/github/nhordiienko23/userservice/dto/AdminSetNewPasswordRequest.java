package com.github.nhordiienko23.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminSetNewPasswordRequest(
        @NotBlank(message = "New password cannot be blank")
        @Size(max = 72, message = "New password must not exceed 72 characters")
        String newPassword
) {}
