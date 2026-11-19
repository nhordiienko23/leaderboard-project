package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.userservice.entity.RefreshToken;
import com.github.nhordiienko23.userservice.exception.InvalidAuthenticationException;
import com.github.nhordiienko23.userservice.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.security.jwt.refresh-token-ttl-days:30}")
    private long refreshTokenTtlDays;

    @Transactional
    public String create(Long userId) {
        String rawToken = generateToken();

        saveToken(userId, rawToken);

        return rawToken;
    }

    @Transactional
    public RotatedRefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidAuthenticationException(
                    "Invalid or expired refresh token"
            );
        }

        String tokenHash = hashToken(rawToken);

        RefreshToken current = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvalidAuthenticationException(
                        "Invalid or expired refresh token"
                ));

        Instant now = Instant.now();

        if (current.getRevokedAt() != null
                || !current.getExpiresAt().isAfter(now)) {
            throw new InvalidAuthenticationException(
                    "Invalid or expired refresh token"
            );
        }

        current.setRevokedAt(now);

        String newRawToken = generateToken();

        saveToken(current.getUserId(), newRawToken);

        return new RotatedRefreshToken(
                current.getUserId(),
                newRawToken
        );
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository
                .findByTokenHashForUpdate(hashToken(rawToken))
                .ifPresent(token -> {
                    if (token.getRevokedAt() == null) {
                        token.setRevokedAt(Instant.now());
                    }
                });
    }
    public record RotatedRefreshToken(Long userId, String rawToken) {
    }

    private void saveToken(Long userId, String rawToken) {
        Instant now = Instant.now();

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hashToken(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusSeconds(refreshTokenTtlDays * 24 * 60 * 60));

        refreshTokenRepository.save(token);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not hash refresh token",
                    exception
            );
        }
    }

}