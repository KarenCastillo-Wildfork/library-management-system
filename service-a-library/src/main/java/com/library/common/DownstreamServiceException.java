package com.library.common;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a call to Service B (the loans service) fails, times out, or is
 * unreachable. Carries the HTTP status this service should answer the client with,
 * so a downstream outage never surfaces as a generic 500.
 */
public class DownstreamServiceException extends RuntimeException {

    private final HttpStatus status;

    public DownstreamServiceException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public DownstreamServiceException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
