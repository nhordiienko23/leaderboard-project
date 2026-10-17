package com.github.nhordiienko23.postservice.entity;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "outbox_entries",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"content_id", "event_type", "action_type"}
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

    @Column(name = "content_id", nullable = false, updatable = false)
    private Long contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false)
    private ActionType actionType;

    @Column(nullable = false)
    private String topic;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "target_author_id")
    private Long targetAuthorId;

    @Column(name = "content_author_id")
    private Long contentAuthorId;

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

    @Column(name = "participants_json", columnDefinition = "TEXT")
    private String participantsJson;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}