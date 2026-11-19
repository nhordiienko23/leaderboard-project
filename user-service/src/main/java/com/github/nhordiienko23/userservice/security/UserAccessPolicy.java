package com.github.nhordiienko23.userservice.security;

import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.entity.UserRole;
import com.github.nhordiienko23.userservice.exception.ForbiddenOperationException;
import com.github.nhordiienko23.userservice.exception.InvalidAuthenticationException;
import com.github.nhordiienko23.userservice.exception.UnauthenticatedException;
import com.github.nhordiienko23.userservice.exception.UserNotFoundException;
import com.github.nhordiienko23.userservice.service.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAccessPolicy {

    private final UserService userService;

    public Long requireAuthenticated(Long userId) {
        if (userId == null || userId <= 0) {
            throw new UnauthenticatedException(
                    "Authentication is required"
            );
        }

        return userId;
    }

    public void requireAdmin(Long adminId) {
        if (adminId == null || adminId <= 0) {
            throw new UnauthenticatedException(
                    "Authentication is required"
            );
        }

        User admin;

        try {
            admin = userService.findUserById(adminId);
        } catch (UserNotFoundException exception) {
            throw new InvalidAuthenticationException(
                    "Authentication required"
            );
        }

        if (admin.getRole() != UserRole.ADMIN) {
            throw new ForbiddenOperationException(
                    "Administrator role required"
            );
        }
    }
}