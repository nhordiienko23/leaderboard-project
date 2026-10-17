package com.github.nhordiienko23.postservice.exception.accessdenied;

public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
