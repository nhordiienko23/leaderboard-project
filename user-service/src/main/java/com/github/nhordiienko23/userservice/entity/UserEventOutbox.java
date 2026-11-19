package com.github.nhordiienko23.userservice.entity;

import com.github.nhordiienko23.leaderboardcontract.event.UserEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_event_outbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 50, updatable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20, updatable = false)
    private UserEventType eventType;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private Long attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @PrePersist
    protected void onCreate() {
        occurredAt = Instant.now();

        if (attempts == null) {
            attempts = 0L;
        }
    }
}