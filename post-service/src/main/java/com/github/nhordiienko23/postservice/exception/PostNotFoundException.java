package com.github.nhordiienko23.postservice.exception;

public class PostNotFoundException extends ResourceNotFoundException {
    public PostNotFoundException(String message) {
        super(message);
    }
}
