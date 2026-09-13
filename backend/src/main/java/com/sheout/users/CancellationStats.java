package com.sheout.users;

import java.time.Instant;

/**
 * How often an account cancels, and whether that has been flagged for a
 * person to look at.
 * <p>
 * A RATE, not just a count, and that distinction is the whole point. A raw
 * count punishes a rider of three years with forty cancellations out of nine
 * hundred trips, while a brand-new account that cancelled three out of three
 * looks clean beside her. Ranking people by a number that mostly measures
 * how long they have used the service is not accountability, it is a
 * seniority penalty.
 * <p>
 * The rate is derived here rather than stored, so it can never disagree with
 * the two counters it comes from.
 */
public record CancellationStats(
        int totalBookings,
        int totalCancellations,
        boolean flagged,
        Instant flaggedAt,
        String flaggedReason
) {

    /**
     * Cancellations as a fraction of bookings, 0 to 1. Zero for an account
     * with no bookings at all - dividing by nothing would otherwise make a
     * brand-new account look infinitely bad.
     */
    public double rate() {
        return totalBookings == 0 ? 0d : (double) totalCancellations / totalBookings;
    }

    /** The same figure as a whole-number percentage, which is how it is read. */
    public int ratePercent() {
        return (int) Math.round(rate() * 100);
    }
}
