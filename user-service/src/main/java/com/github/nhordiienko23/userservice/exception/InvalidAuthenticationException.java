package com.github.nhordiienko23.userservice.exception;

public class InvalidAuthenticationException extends RuntimeException {

    public InvalidAuthenticationException(String message) {
        super(message);
    }
}