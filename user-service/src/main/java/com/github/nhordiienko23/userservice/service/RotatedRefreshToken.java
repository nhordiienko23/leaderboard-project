package com.github.nhordiienko23.userservice.service;

public record RotatedRefreshToken(
        Long userId,
        String refreshToken
) {
}