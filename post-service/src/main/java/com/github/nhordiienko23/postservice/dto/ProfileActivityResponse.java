package com.github.nhordiienko23.postservice.dto;

public record ProfileActivityResponse(
        long postsCreated,
        long commentsAuthored,
        long postLikesGiven,
        long commentLikesGiven,
        long postLikesReceived,
        long commentLikesReceived
) {
}
