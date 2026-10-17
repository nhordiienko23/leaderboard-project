package com.github.nhordiienko23.postservice.exception.notfound;

public class PostNotFoundException extends ResourceNotFoundException {
    public PostNotFoundException(String message) {
        super(message);
    }
}
