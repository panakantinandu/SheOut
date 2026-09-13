package com.sheout.users.internal;

import com.sheout.users.TrustStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the fairness properties of the trust flag.
 * <p>
 * The two minimum-count floors are what these tests exist for. Without
 * them, one cancellation on a first-ever booking is a rate of 100% and one
 * furious rider on a partner's first night is an average of 1.0 - and both
 * would open a case against somebody on the strength of a single event.
 * They are the kind of rule that looks like an optimisation and gets
 * removed by somebody simplifying a condition.
 */
class TrustPolicyTest {

    private final TrustPolicy policy = new TrustPolicy(0.4, 5, 3.5, 5);

    private static TrustStats cancellations(int bookings, int cancelled) {
        return new TrustStats(bookings, cancelled, null, 0, false, null, null);
    }

    private static TrustStats ratings(double average, int count) {
        return new TrustStats(0, 0, average, count, false, null, null);
    }

    @Test
    @DisplayName("no cancellation flag until there is enough history for a rate to mean anything")
    void doesNotFlagCancellationsTooEarly() {
        assertTrue(policy.reviewReason(cancellations(1, 1)).isEmpty());
        assertTrue(policy.reviewReason(cancellations(4, 4)).isEmpty());
    }

    @Test
    @DisplayName("flags only once the cancellation rate is genuinely above the threshold")
    void flagsCancellationsAboveThreshold() {
        // Exactly at the line is not above it - 40% of 5 is 2, and a
        // threshold somebody has merely reached is not one they crossed.
        assertTrue(policy.reviewReason(cancellations(5, 2)).isEmpty());
        assertTrue(policy.reviewReason(cancellations(5, 3)).isPresent());
    }

    @Test
    @DisplayName("a long history is not punished for an old run of cancellations")
    void ratePreventsSeniorityPenalty() {
        // The whole point of a rate over a count: 40 cancellations look
        // alarming until you see the 900 trips around them.
        assertTrue(policy.reviewReason(cancellations(900, 40)).isEmpty());
        assertTrue(policy.reviewReason(cancellations(6, 5)).isPresent());
    }

    @Test
    @DisplayName("no rating flag until enough people have rated")
    void doesNotFlagRatingsTooEarly() {
        assertTrue(policy.reviewReason(ratings(1.0, 1)).isEmpty());
        assertTrue(policy.reviewReason(ratings(1.0, 4)).isEmpty());
        assertTrue(policy.reviewReason(ratings(2.0, 5)).isPresent());
    }

    @Test
    @DisplayName("a rating exactly at the threshold is not below it")
    void flagsRatingsBelowThreshold() {
        assertTrue(policy.reviewReason(ratings(3.5, 10)).isEmpty());
        assertTrue(policy.reviewReason(ratings(3.49, 10)).isPresent());
    }

    @Test
    @DisplayName("never rated is not the same as rated badly")
    void unratedAccountIsNotFlagged() {
        assertTrue(policy.reviewReason(new TrustStats(0, 0, null, 0, false, null, null)).isEmpty());
    }

    @Test
    @DisplayName("an account failing both signals says so once, with both reasons")
    void bothReasonsInOneFlag() {
        Optional<String> reason = policy.reviewReason(
                new TrustStats(10, 8, 2.0, 9, false, null, null));
        assertTrue(reason.isPresent());
        assertTrue(reason.get().contains("Cancelled 8 of 10"), reason.get());
        assertTrue(reason.get().contains("rated 2.00"), reason.get());
        // Both facts in one sentence, not two rows - see
        // AdminService.trustReviewQueue for why the queue has one entry per
        // account rather than one per reason.
        assertEquals(2, reason.get().split("; ").length, reason.get());
    }

    @Test
    @DisplayName("a healthy account is never flagged")
    void healthyAccountIsClean() {
        assertFalse(policy.reviewReason(new TrustStats(50, 2, 4.8, 40, false, null, null)).isPresent());
    }
}
