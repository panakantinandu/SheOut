package com.sheout.dispatch.internal.redis;

import com.sheout.booking.BookingCategory;

import java.time.Instant;
import java.util.UUID;

/**
 * Public - read by DispatchService (com.sheout.dispatch.internal) across
 * the sub-package boundary.
 * <p>
 * Carries pickup/category alongside the retry bookkeeping (attempt,
 * radiusKm) because BookingApi has no read method (only requestBooking/
 * assignDriver) - a retry round has nothing else to re-fetch this from,
 * so dispatch captures it itself from the original BookingRequested event
 * and re-persists it on every round.
 * <p>
 * {@code searchStartedAt} and {@code searchDeadline} belong to the SEARCH,
 * not to the round: they are set once when the first round runs and copied
 * forward verbatim through every retry. Two consequences are deliberate.
 * Changing the configured timeout cannot move the deadline of a search
 * already in flight, so nobody's wait is silently extended or cut short by
 * a restart with different settings. And because the deadline is an absolute
 * instant rather than a remaining budget, a slow sweep, a pause, or an
 * application restart cannot quietly hand a search more time than it was
 * given.
 * <p>
 * Do not confuse {@code searchDeadline} with the per-offer accept window.
 * The window is how long ONE driver has to answer ONE offer (15s by
 * default). The deadline is how long the whole hunt may take across every
 * round and radius expansion. They bound different things and are allowed
 * to expire independently.
 */
public record RoundState(
        int attempt,
        double radiusKm,
        double pickupLat,
        double pickupLng,
        BookingCategory category,
        /** Whose booking this is, so an abandoned search can name her without asking booking. */
        UUID customerId,
        Instant searchStartedAt,
        Instant searchDeadline
) {

    /** The next round, same search: the deadline and the customer travel on unchanged. */
    public RoundState nextAttempt(double nextRadiusKm) {
        return new RoundState(
                attempt + 1, nextRadiusKm, pickupLat, pickupLng, category,
                customerId, searchStartedAt, searchDeadline);
    }

    /** At the deadline the search is over, not nearly over. Inclusive on purpose. */
    public boolean deadlinePassed(Instant now) {
        return !now.isBefore(searchDeadline);
    }

    /**
     * True when another full round would run past the deadline.
     * <p>
     * STRICTLY after, and the difference is not pedantry - it was a bug. A
     * round that finishes exactly on the deadline is inside the budget, and
     * refusing it threw away half of a 30-second budget in testing: two
     * 15-second rounds fit exactly, and an inclusive comparison rejected the
     * second one and gave up at 16 seconds.
     * <p>
     * The look-ahead exists at all because rounds are only examined when one
     * expires. Without it, a round starting a second before the deadline
     * still runs a whole offer window past it, so a 90-second budget gave up
     * at about 104 seconds. This asks the only question that matters: is
     * there room for another whole round?
     */
    public boolean noRoomForAnotherRound(Instant now, long offerWindowSeconds) {
        return now.plusSeconds(offerWindowSeconds).isAfter(searchDeadline);
    }
}
