package com.github.nhordiienko23.userservice.dto;

import com.github.nhordiienko23.userservice.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull UserRole role
) {
}