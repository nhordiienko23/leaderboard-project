package com.github.nhordiienko23.leaderboardservice.dto;

import lombok.Builder;

@Builder
public record LeaderboardEntryDTO(
        Long userId,
        String username,
        Long points
) {
}
