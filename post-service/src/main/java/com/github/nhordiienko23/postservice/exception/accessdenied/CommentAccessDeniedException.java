package com.github.nhordiienko23.postservice.exception.accessdenied;

public class CommentAccessDeniedException extends AccessDeniedException {

    public CommentAccessDeniedException(String message) {
        super(message);
    }
}
