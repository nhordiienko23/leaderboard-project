package com.github.nhordiienko23.leaderboardservice.entity;

import com.github.nhordiienko23.leaderboardcontract.event.UserEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_user_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedUserEvent {

        @Id
        @Column(name = "event_id", nullable = false, updatable = false)
        private Long eventId;

        @Column(name = "user_id", nullable = false, updatable = false)
        private Long userId;

        @Column(name = "username", nullable = false, updatable = false)
        private String username;

        @Enumerated(EnumType.STRING)
        @Column(name = "event_type", nullable = false, updatable = false)
        private UserEventType eventType;

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;

        @PrePersist
        protected void onCreate() {
                if (createdAt == null) {
                        createdAt = LocalDateTime.now();
                }
        }
}