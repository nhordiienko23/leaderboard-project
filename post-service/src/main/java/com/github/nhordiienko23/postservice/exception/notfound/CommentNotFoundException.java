package com.github.nhordiienko23.postservice.exception.notfound;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class CommentNotFoundException extends ResponseStatusException {

    public CommentNotFoundException(Long commentId, Long postId) {
        super(
                HttpStatus.NOT_FOUND,
                "Comment " + commentId + " was not found for post " + postId
        );
    }
}