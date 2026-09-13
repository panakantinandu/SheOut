package com.sheout.users.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the fairness properties of the cancellation flag.
 * <p>
 * The minimum-bookings floor is the one worth guarding: without it, a
 * brand-new account that cancelled her first booking has a rate of 100% and
 * gets an open case against her before her second trip. That is the kind of
 * rule that looks like an optimisation and gets removed by somebody
 * simplifying the condition.
 */
class CancellationPolicyTest {

    private final CancellationPolicy policy = new CancellationPolicy(0.4, 5);

    @Test
    @DisplayName("no flag until there is enough history for a rate to mean anything")
    void doesNotFlagTooEarly() {
        assertFalse(policy.shouldFlag(1, 1));
        assertFalse(policy.shouldFlag(2, 2));
        assertFalse(policy.shouldFlag(4, 4));
    }

    @Test
    @DisplayName("flags only once the rate is genuinely above the threshold")
    void flagsAboveThreshold() {
        // Exactly at the line is not above it - 40% of 5 is 2, and a
        // threshold somebody has merely reached is not one they crossed.
        assertFalse(policy.shouldFlag(5, 2));
        assertTrue(policy.shouldFlag(5, 3));
        assertTrue(policy.shouldFlag(10, 7));
    }

    @Test
    @DisplayName("does not punish a long history for an old run of cancellations")
    void ratePreventsSeniorityPenalty() {
        // The whole point of a rate over a count: 40 cancellations look
        // alarming until you see the 900 trips around them.
        assertFalse(policy.shouldFlag(900, 40));
        assertTrue(policy.shouldFlag(6, 5));
    }

    @Test
    @DisplayName("an account with no history is never flagged")
    void neverFlagsAnEmptyAccount() {
        assertFalse(policy.shouldFlag(0, 0));
    }
}
