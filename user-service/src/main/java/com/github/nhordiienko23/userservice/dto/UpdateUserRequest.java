package com.github.nhordiienko23.userservice.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;

import jakarta.validation.constraints.Size;


public record UpdateUserRequest(
        @Size(max = 50, message = "Username must not exceed 50 characters")
        @Nullable
        String username,

        @Size(max = 254, message = "Email must not exceed 254 characters")
        @Email(message = "Email must be a valid email address")
        @Nullable
        String email
) {}
