package com.github.nhordiienko23.userservice.dto;

import com.github.nhordiienko23.userservice.entity.UserRole;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        UserRole role,
        LocalDateTime registeredAt,
        LocalDateTime lastLoginAt,
        LocalDateTime updatedAt
) {}
