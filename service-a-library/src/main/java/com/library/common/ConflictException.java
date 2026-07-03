package com.library.common;

/** Thrown when a request conflicts with the current state (e.g. duplicate ISBN/username). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
