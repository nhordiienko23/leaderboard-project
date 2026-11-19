package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.userservice.entity.RefreshToken;
import com.github.nhordiienko23.userservice.exception.InvalidAuthenticationException;
import com.github.nhordiienko23.userservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Refresh token service tests")
class RefreshTokenServiceTest {

    private static final long DEFAULT_TTL_DAYS = 30;
    private static final String INVALID_TOKEN_MESSAGE =
            "Invalid or expired refresh token";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
        ReflectionTestUtils.setField(
                refreshTokenService,
                "refreshTokenTtlDays",
                DEFAULT_TTL_DAYS
        );
    }

    @Test
    @DisplayName("Should create a URL-safe token and persist only its hash")
    void createGeneratesTokenAndPersistsHash() {
        Long userId = 42L;
        Instant before = Instant.now();

        String rawToken = refreshTokenService.create(userId);

        Instant after = Instant.now();

        ArgumentCaptor<RefreshToken> tokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        RefreshToken savedToken = tokenCaptor.getValue();

        assertTrue(rawToken.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(32, Base64.getUrlDecoder().decode(rawToken).length);
        assertEquals(userId, savedToken.getUserId());
        assertEquals(hash(rawToken), savedToken.getTokenHash());
        assertFalse(savedToken.getTokenHash().contains(rawToken));
        assertNotNull(savedToken.getCreatedAt());
        assertFalse(savedToken.getCreatedAt().isBefore(before));
        assertFalse(savedToken.getCreatedAt().isAfter(after));
        assertEquals(
                savedToken.getCreatedAt().plus(Duration.ofDays(DEFAULT_TTL_DAYS)),
                savedToken.getExpiresAt()
        );
        assertNull(savedToken.getRevokedAt());
    }

    @Test
    @DisplayName("Should use the configured token lifetime")
    void createUsesConfiguredTokenLifetime() {
        ReflectionTestUtils.setField(
                refreshTokenService,
                "refreshTokenTtlDays",
                2L
        );

        refreshTokenService.create(42L);

        ArgumentCaptor<RefreshToken> tokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        assertEquals(
                tokenCaptor.getValue().getCreatedAt().plus(Duration.ofDays(2)),
                tokenCaptor.getValue().getExpiresAt()
        );
    }

    @Test
    @DisplayName("Should rotate a valid token and revoke the old token")
    void rotateRevokesCurrentTokenAndSavesReplacement() {
        String rawToken = "existing-refresh-token";
        Long userId = 42L;

        RefreshToken current = activeToken(userId);
        current.setTokenHash(hash(rawToken));

        when(refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken)))
                .thenReturn(Optional.of(current));

        Instant before = Instant.now();

        RefreshTokenService.RotatedRefreshToken rotated =
                refreshTokenService.rotate(rawToken);

        Instant after = Instant.now();

        ArgumentCaptor<RefreshToken> tokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        RefreshToken savedToken = tokenCaptor.getValue();

        assertEquals(userId, rotated.userId());
        assertTrue(rotated.rawToken().matches("[A-Za-z0-9_-]{43}"));

        assertEquals(userId, savedToken.getUserId());
        assertEquals(hash(rotated.rawToken()), savedToken.getTokenHash());

        assertEquals(hash(rawToken), current.getTokenHash());
        assertNotNull(current.getRevokedAt());
        assertFalse(current.getRevokedAt().isBefore(before));
        assertFalse(current.getRevokedAt().isAfter(after));

        assertEquals(
                savedToken.getCreatedAt().plus(Duration.ofDays(DEFAULT_TTL_DAYS)),
                savedToken.getExpiresAt()
        );
    }

    @Test
    @DisplayName("Should reject a null refresh token without accessing the repository")
    void rotateRejectsNullToken() {
        InvalidAuthenticationException exception = assertThrows(
                InvalidAuthenticationException.class,
                () -> refreshTokenService.rotate(null)
        );

        assertEquals(INVALID_TOKEN_MESSAGE, exception.getMessage());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("Should reject a blank refresh token without accessing the repository")
    void rotateRejectsBlankToken() {
        InvalidAuthenticationException exception = assertThrows(
                InvalidAuthenticationException.class,
                () -> refreshTokenService.rotate(" \t ")
        );

        assertEquals(INVALID_TOKEN_MESSAGE, exception.getMessage());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("Should reject a refresh token that does not exist")
    void rotateRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHashForUpdate(hash("unknown")))
                .thenReturn(Optional.empty());

        InvalidAuthenticationException exception = assertThrows(
                InvalidAuthenticationException.class,
                () -> refreshTokenService.rotate("unknown")
        );

        assertEquals(INVALID_TOKEN_MESSAGE, exception.getMessage());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should reject an already revoked refresh token")
    void rotateRejectsRevokedToken() {
        RefreshToken current = activeToken(42L);
        current.setRevokedAt(Instant.now().minusSeconds(1));

        when(refreshTokenRepository.findByTokenHashForUpdate(hash("revoked")))
                .thenReturn(Optional.of(current));

        assertThrows(
                InvalidAuthenticationException.class,
                () -> refreshTokenService.rotate("revoked")
        );

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should reject an expired refresh token")
    void rotateRejectsExpiredToken() {
        RefreshToken current = activeToken(42L);
        current.setExpiresAt(Instant.now());

        when(refreshTokenRepository.findByTokenHashForUpdate(hash("expired")))
                .thenReturn(Optional.of(current));

        assertThrows(
                InvalidAuthenticationException.class,
                () -> refreshTokenService.rotate("expired")
        );

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should ignore null and blank tokens when revoking")
    void revokeIgnoresNullAndBlankTokens() {
        refreshTokenService.revoke(null);
        refreshTokenService.revoke("  ");

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("Should revoke an active token")
    void revokeMarksActiveTokenRevoked() {
        RefreshToken token = activeToken(42L);

        when(refreshTokenRepository.findByTokenHashForUpdate(hash("active")))
                .thenReturn(Optional.of(token));

        Instant before = Instant.now();

        refreshTokenService.revoke("active");

        Instant after = Instant.now();

        assertNotNull(token.getRevokedAt());
        assertFalse(token.getRevokedAt().isBefore(before));
        assertFalse(token.getRevokedAt().isAfter(after));
        verify(refreshTokenRepository).findByTokenHashForUpdate(hash("active"));
    }

    @Test
    @DisplayName("Should leave an already revoked token unchanged")
    void revokeLeavesAlreadyRevokedTokenUnchanged() {
        Instant revokedAt = Instant.now().minusSeconds(30);
        RefreshToken token = activeToken(42L);
        token.setRevokedAt(revokedAt);

        when(refreshTokenRepository.findByTokenHashForUpdate(hash("revoked")))
                .thenReturn(Optional.of(token));

        refreshTokenService.revoke("revoked");

        assertEquals(revokedAt, token.getRevokedAt());
    }

    @Test
    @DisplayName("Should do nothing when revoking a token that does not exist")
    void revokeIgnoresUnknownToken() {
        when(refreshTokenRepository.findByTokenHashForUpdate(hash("unknown")))
                .thenReturn(Optional.empty());

        refreshTokenService.revoke("unknown");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    private RefreshToken activeToken(Long userId) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash("stored-token"));
        token.setCreatedAt(Instant.now().minusSeconds(60));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        return token;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
