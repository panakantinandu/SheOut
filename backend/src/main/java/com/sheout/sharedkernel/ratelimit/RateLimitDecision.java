package com.sheout.sharedkernel.ratelimit;

/**
 * The answer to one attempt.
 *
 * @param retryAfterSeconds when refused, how long until the window that
 *                          refused it resets. Always at least 1, so a client
 *                          told to wait is never told to wait zero seconds
 *                          and retry in a tight loop.
 */
public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, 0);
    }

    public static RateLimitDecision refuse(long retryAfterSeconds) {
        return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
    }

    /** Throws when refused, so a controller can guard in one line. */
    public void orThrow(String message) {
        if (!allowed) {
            throw new TooManyRequestsException(message, retryAfterSeconds);
        }
    }
}
