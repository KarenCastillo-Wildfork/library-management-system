package com.library.common;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error payload returned by every endpoint in this service, whether the
 * failure originates locally (validation, not found) or from a downstream call to
 * Service B.
 *
 * @param timestamp moment the error was produced
 * @param status    HTTP status code
 * @param error     short HTTP reason phrase (e.g. "Not Found")
 * @param message   human-readable description of what went wrong
 * @param path      request path that triggered the error
 * @param details   optional list of field-level validation messages
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path, List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, path, details);
    }
}
