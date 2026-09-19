package com.sheout.sharedkernel.ratelimit;

import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A 429 that carries how long to wait. GlobalExceptionHandler turns
 * retryAfterSeconds into a Retry-After header, so a refusal is something a
 * client can act on rather than a failure it can only retry blindly.
 */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        this("Too Many Requests", message, retryAfterSeconds);
    }

    public TooManyRequestsException(String error, String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, error, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
