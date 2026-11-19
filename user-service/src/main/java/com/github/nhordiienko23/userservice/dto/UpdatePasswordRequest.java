package com.github.nhordiienko23.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePasswordRequest(
        @NotBlank(message = "Current password cannot be blank")
        @Size(max = 72, message = "Current password must not exceed 72 characters")
        String currentPassword,

        @NotBlank(message = "New password cannot be blank")
        @Size(max = 72, message = "New password must not exceed 72 characters")
        String newPassword
) {}
