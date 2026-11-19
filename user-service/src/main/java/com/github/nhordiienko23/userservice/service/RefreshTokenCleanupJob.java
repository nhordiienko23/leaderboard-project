package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.userservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanExpiredTokens() {
        Instant now = Instant.now();
        Instant revokedBefore = now.minus(Duration.ofDays(1));

        int deleted = refreshTokenRepository.deleteExpiredOrOldRevoked(
                now,
                revokedBefore
        );

        if (deleted > 0) {
            log.info("Refresh token cleanup completed: deleted={}", deleted);
        }
    }
}