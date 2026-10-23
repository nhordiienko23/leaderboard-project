package com.github.nhordiienko23.userservice.dto;

import com.github.nhordiienko23.userservice.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank(message = "Username cannot be blank")
        @Size(max = 50, message = "Username must not exceed 50 characters")
        String username,

        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Email must be a valid email address")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @NotBlank(message = "Password cannot be blank")
        @Size(max = 72, message = "Password must not exceed 72 characters")
        String password,

        @NotNull(message = "Role cannot be null")
        UserRole role
) {
}
