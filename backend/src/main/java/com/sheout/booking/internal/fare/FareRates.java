package com.sheout.booking.internal.fare;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * The tunable numbers behind one category's price.
 * <p>
 * Every one of them is configuration, and none is a literal in the pricing
 * code. Pricing is the thing most likely to need changing at short notice -
 * a competitor moves, drivers say a rate does not cover fuel, a corridor
 * turns out to be slower than assumed - and needing a redeploy to answer
 * any of that is how a platform ends up stuck with a price it knows is
 * wrong.
 * <p>
 * Per category, because a bike ride and a parcel run have genuinely
 * different costs. A parcel has no passenger, so waiting time is cheaper
 * and the floor can sit lower; a ride carries someone, and the time
 * component is what keeps a partner whole in traffic.
 * <p>
 * THESE ARE A STARTING HYPOTHESIS, NOT A PRICE. The defaults are shaped
 * from Rapido's published Hyderabad structure so they land in a plausible
 * range rather than being invented, but they have not been checked against
 * what it actually costs a woman here to run a scooter for an hour. The
 * first real driver feedback should move them.
 */
public record FareRates(
        BigDecimal baseFare,
        BigDecimal perKmRate,
        BigDecimal perMinuteRate,
        BigDecimal minimumFare,
        BigDecimal nightMultiplier,
        LocalTime nightWindowStart,
        LocalTime nightWindowEnd,
        /**
         * The slot real surge pricing will fill. 1.0 means no surge.
         * <p>
         * Config-driven and flat for now, deliberately: demand-responsive
         * pricing is a much larger piece of work, and wiring the multiplier
         * into the formula now means that work becomes an implementation
         * behind this number rather than another rewrite of the formula.
         */
        BigDecimal surgeMultiplier
) {

    /**
     * Whether a time falls inside the night window.
     * <p>
     * Handles a window that wraps past midnight, which the default one does:
     * 22:00 to 06:00 is not a range you can test with a simple between.
     * Getting that wrong is the kind of bug that only shows up at night, on
     * exactly the trips where the multiplier matters most - a woman
     * travelling alone at 2am is the person this surcharge is meant to get a
     * partner out of bed for.
     * <p>
     * Start is inclusive and end exclusive, so a window of 22:00-06:00
     * charges the multiplier at 22:00:00 and not at 06:00:00.
     */
    public boolean isNight(LocalTime at) {
        if (nightWindowStart.equals(nightWindowEnd)) {
            // A zero-length window means the surcharge is switched off,
            // which is a reasonable thing to want and should not be read as
            // "always night".
            return false;
        }
        if (nightWindowStart.isBefore(nightWindowEnd)) {
            return !at.isBefore(nightWindowStart) && at.isBefore(nightWindowEnd);
        }
        // Wraps midnight: inside if it is at or after the start, OR before
        // the end.
        return !at.isBefore(nightWindowStart) || at.isBefore(nightWindowEnd);
    }
}
