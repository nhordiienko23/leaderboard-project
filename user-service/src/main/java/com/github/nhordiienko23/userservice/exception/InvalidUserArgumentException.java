package com.github.nhordiienko23.userservice.exception;

public class InvalidUserArgumentException extends RuntimeException {
    public InvalidUserArgumentException(String message) {
        super(message);
    }
}
