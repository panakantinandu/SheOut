package com.sheout.users;

import java.time.Instant;

/**
 * Everything about an account that bears on whether it should be looked at:
 * how often it cancels, how it is rated, and whether either has already put
 * it in front of an operator.
 * <p>
 * One record rather than one per signal, because there is one flag. An
 * account is either waiting for review or it is not, and the reason says
 * which of these numbers put it there - possibly both. Two parallel flags
 * would mean an account could be cleared of one and silently stay flagged
 * for the other, and an operator working the queue could never tell whether
 * they had finished.
 * <p>
 * Rates are derived here rather than stored, so they can never disagree with
 * the counters they come from.
 */
public record TrustStats(
        int totalBookings,
        int totalCancellations,
        /** Null when nobody has rated this account yet, which is not the same as a low score. */
        Double averageStars,
        int totalRatings,
        boolean flagged,
        Instant flaggedAt,
        String flaggedReason
) {

    public static TrustStats empty() {
        return new TrustStats(0, 0, null, 0, false, null, null);
    }

    /**
     * Cancellations as a fraction of bookings, 0 to 1.
     * <p>
     * A rate and not a count, deliberately. A raw count punishes a rider of
     * three years with forty cancellations out of nine hundred trips, and
     * lets a brand-new account that cancelled three out of three look clean
     * beside her. Ranking people by a number that mostly measures how long
     * they have used the service is not accountability.
     * <p>
     * Zero for an account with no bookings at all - dividing by nothing
     * would otherwise make a brand-new account look infinitely bad.
     */
    public double cancellationRate() {
        return totalBookings == 0 ? 0d : (double) totalCancellations / totalBookings;
    }

    /** The same figure as a whole-number percentage, which is how it is read. */
    public int cancellationRatePercent() {
        return (int) Math.round(cancellationRate() * 100);
    }

    public boolean hasRatings() {
        return totalRatings > 0 && averageStars != null;
    }
}
