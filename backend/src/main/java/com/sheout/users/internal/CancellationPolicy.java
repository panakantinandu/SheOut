package com.sheout.users.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * When a cancellation rate is high enough to be worth a person's attention.
 * <p>
 * Configurable, because where this line sits is a business judgement that
 * will move with experience, not a constant.
 * <p>
 * A FLAG, never a block. Crossing the line puts an account in front of an
 * operator with the numbers attached; it does not take anything away. That
 * is the same rule driver verification already follows here - a human makes
 * the call - and it matters more, not less, for cancellations: a rider who
 * cancels a lot may be dodging fares, or may be repeatedly abandoned by
 * partners who never arrive. The rate cannot tell those apart. A person
 * reading the reasons can.
 */
@Component
public class CancellationPolicy {

    private static final Logger log = LoggerFactory.getLogger(CancellationPolicy.class);

    private final double warningThreshold;
    private final int minimumBookings;

    CancellationPolicy(
            @Value("${sheout.users.cancellation-rate-warning-threshold:0.4}") double warningThreshold,
            @Value("${sheout.users.cancellation-minimum-bookings:5}") int minimumBookings) {
        this.warningThreshold = warningThreshold;
        this.minimumBookings = minimumBookings;
        log.info("Cancellation review threshold: rate above {} after at least {} bookings",
                warningThreshold, minimumBookings);
    }

    /**
     * True when this account should be put in front of an operator.
     * <p>
     * The minimum-bookings floor is not a nicety, it is what stops the
     * feature being unfair on its first day: without it, one cancellation on
     * a first-ever booking is a rate of 100%, and every new user who changed
     * her mind once would be flagged for review. Nobody's second trip should
     * start with an open case against them.
     */
    public boolean shouldFlag(int totalBookings, int totalCancellations) {
        if (totalBookings < minimumBookings) {
            return false;
        }
        return (double) totalCancellations / totalBookings > warningThreshold;
    }

    public String describe(int totalBookings, int totalCancellations) {
        int percent = (int) Math.round(100.0 * totalCancellations / Math.max(totalBookings, 1));
        return "Cancelled %d of %d bookings (%d%%), above the %d%% review threshold"
                .formatted(totalCancellations, totalBookings, percent, Math.round(warningThreshold * 100));
    }

    public double warningThreshold() {
        return warningThreshold;
    }

    public int minimumBookings() {
        return minimumBookings;
    }
}
