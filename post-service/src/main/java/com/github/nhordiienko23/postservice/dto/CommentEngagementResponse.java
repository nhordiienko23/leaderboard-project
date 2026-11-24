package com.github.nhordiienko23.postservice.dto;

public record CommentEngagementResponse(
        long likeCount,
        Boolean likedByMe
) {
}
