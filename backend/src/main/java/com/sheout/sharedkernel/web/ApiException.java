package com.sheout.sharedkernel.web;

import org.springframework.http.HttpStatus;

/**
 * Cross-cutting HTTP-layer error (not found, forbidden, unauthorized, ...)
 * any module can throw from a controller to get back the standard
 * {@link ApiErrorResponse} shape with the right status code, without each
 * module re-inventing its own exception-to-response mapping.
 * <p>
 * Reserve this for access-control / request-shape problems. Expected
 * business outcomes (invalid OTP, wrong state transition, etc.) should be
 * modeled with {@link com.sheout.sharedkernel.Result} at the module's
 * public API instead, and mapped to a response by the controller.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    public ApiException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "Forbidden", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "Not Found", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String error() {
        return error;
    }
}
