package com.github.nhordiienko23.postservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "likes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_likes_user_post",
                        columnNames = {"user_id", "post_id"}
                ),
                @UniqueConstraint(
                        name = "uk_likes_user_comment",
                        columnNames = {"user_id", "comment_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private Comment comment;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        validateTarget();
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        validateTarget();
    }

    private void validateTarget() {
        if ((post == null) == (comment == null)) {
            throw new IllegalStateException(
                    "A like must target exactly one post or one comment"
            );
        }
    }
}