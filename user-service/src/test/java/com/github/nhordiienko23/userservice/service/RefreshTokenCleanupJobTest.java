package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.userservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Refresh token cleanup job tests")
class RefreshTokenCleanupJobTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("Should delete expired tokens and use a 24-hour revoked-token cutoff")
    void cleanExpiredTokensUsesCurrentTimeAndOneDayCutoff() {
        when(refreshTokenRepository.deleteExpiredOrOldRevoked(any(), any()))
                .thenReturn(3);
        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenRepository);

        Instant before = Instant.now();
        job.cleanExpiredTokens();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> revokedBeforeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deleteExpiredOrOldRevoked(
                nowCaptor.capture(), revokedBeforeCaptor.capture()
        );

        Instant now = nowCaptor.getValue();
        assertFalse(now.isBefore(before));
        assertFalse(now.isAfter(after));
        assertEquals(now.minus(Duration.ofDays(1)), revokedBeforeCaptor.getValue());
    }

    @Test
    @DisplayName("Should call the repository when no tokens are deleted")
    void cleanExpiredTokensCallsRepositoryWhenNothingIsDeleted() {
        when(refreshTokenRepository.deleteExpiredOrOldRevoked(any(), any()))
                .thenReturn(0);
        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenRepository);

        job.cleanExpiredTokens();

        verify(refreshTokenRepository).deleteExpiredOrOldRevoked(any(), any());
    }
}