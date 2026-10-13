package com.github.nhordiienko23.leaderboardservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "processed_posts")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ProcessedPost {
    @Id
    @Column(name = "post_id",nullable = false,updatable = false)
    private Long postId;
}
