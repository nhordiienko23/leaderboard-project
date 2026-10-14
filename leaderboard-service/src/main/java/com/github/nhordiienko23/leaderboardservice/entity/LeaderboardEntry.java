package com.github.nhordiienko23.leaderboardservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "leaderboard_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaderboardEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private Long points;
}