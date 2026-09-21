package com.sheout.sharedkernel.ratelimit;

import java.time.Duration;

/**
 * Counts attempts against a named budget and says whether one more is
 * allowed.
 * <p>
 * Every call counts, allowed or not. What is being limited is how often
 * somebody tries, not how often they succeed: a brute-force guesser whose
 * refused attempts were free would simply keep trying.
 * <p>
 * Callers own the policy - which key, how many, over what window - because
 * the right numbers differ wildly between an SMS endpoint and a pickup code.
 * This interface only owns the counting, so every limit in the codebase is
 * counted the same way and holds across instances.
 */
public interface RateLimiter {

    /**
     * Records one attempt against {@code key} and says whether it fits in
     * {@code limit} attempts per {@code window}.
     *
     * @param key    a namespaced subject, e.g. {@code "otp-request:phone:+91..."}
     */
    RateLimitDecision tryConsume(String key, int limit, Duration window);

    /**
     * Gives back one attempt recorded against {@code key}, for a limit that
     * is meant to count only failures. The attempt is still consumed before
     * the outcome is known - so a subject already over its limit is refused
     * even when it is right - and returned once it turns out to have
     * succeeded. Never takes a count below zero.
     */
    void release(String key);
}
