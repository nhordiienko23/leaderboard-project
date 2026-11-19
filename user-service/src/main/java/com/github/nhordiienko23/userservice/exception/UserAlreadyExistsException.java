package com.github.nhordiienko23.userservice.exception;

public class UserAlreadyExistsException extends InvalidUserArgumentException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
