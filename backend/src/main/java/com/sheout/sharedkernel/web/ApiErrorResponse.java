package com.sheout.sharedkernel.web;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response body every module's REST endpoints must return on
 * failure, so API consumers (customer app, driver app, admin) only ever
 * parse one error shape.
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiErrorResponse of(int status, String error, String message, String path, List<String> details) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, details);
    }
}
