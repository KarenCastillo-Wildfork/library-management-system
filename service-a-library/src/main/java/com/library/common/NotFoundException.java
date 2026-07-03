package com.library.common;

/** Thrown when a requested resource (book, user, loan) does not exist. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
