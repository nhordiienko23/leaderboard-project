package com.github.nhordiienko23.postservice.entity;

import com.github.nhordiienko23.postservice.kafka.ActionType;
import com.github.nhordiienko23.postservice.kafka.EventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "outbox_entries",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"event_id", "event_type", "action_type"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private EventType eventType;

    @Column(nullable = false)
    private String topic;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false)
    private ActionType actionType;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private Long attempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}