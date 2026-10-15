package com.github.nhordiienko23.leaderboardservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"event_id", "event_type", "action_type"}
        )
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ProcessedEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "action_type",nullable = false)
    private String actionType;

    private LocalDateTime createdAt;


    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
