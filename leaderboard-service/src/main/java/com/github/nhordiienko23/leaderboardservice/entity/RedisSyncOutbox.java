package com.github.nhordiienko23.leaderboardservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "redis_sync_outbox",
        indexes = @Index(
                name = "idx_redis_sync_outbox_processed_id",
                columnList = "processed, id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedisSyncOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean processed;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

    }
}