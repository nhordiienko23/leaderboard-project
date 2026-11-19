package com.github.nhordiienko23.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Username or email cannot be blank")
        @Size(max = 254, message = "Username or email must not exceed 254 characters")
        String login,

        @NotBlank(message = "Password cannot be blank")
        @Size(max = 72, message = "Password must not exceed 72 characters")
        String password
) {
}