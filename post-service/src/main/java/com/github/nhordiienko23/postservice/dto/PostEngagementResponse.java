package com.github.nhordiienko23.postservice.dto;

public record PostEngagementResponse(
        long likeCount,
        long commentCount,
        Boolean likedByMe
) {
}
