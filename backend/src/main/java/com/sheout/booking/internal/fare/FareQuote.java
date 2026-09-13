package com.sheout.booking.internal.fare;

import java.math.BigDecimal;

/**
 * What a fare works out to, and every step of how it got there.
 * <p>
 * It used to be an amount and a distance. It carries the whole breakdown
 * now because three different people need to read this number and none of
 * them can take it on trust: a rider asking why a short trip cost what it
 * did, a partner checking her payout is what she was told, and whoever is
 * tuning the rates trying to work out which component is wrong. A single
 * total answers none of those questions.
 * <p>
 * It is also the shape the driver-facing payout breakdown needs, which is
 * the transparency commitment this project has already made elsewhere - a
 * partner should never have to take our arithmetic on faith.
 */
public record FareQuote(
        /** What the rider pays. Every other field explains this one. */
        BigDecimal amount,

        double distanceKm,
        double durationMinutes,

        /** True when distance and duration came from a real road route rather than an estimate. */
        boolean routed,

        BigDecimal baseFare,
        /** perKmRate x distanceKm. */
        BigDecimal distanceCharge,
        /** perMinuteRate x durationMinutes. */
        BigDecimal timeCharge,

        /** 1.0 when no surge is configured. */
        BigDecimal surgeMultiplier,
        /** 1.0 when the trip is outside the night window. */
        BigDecimal nightMultiplier,

        /**
         * True when the calculated fare came out below the floor and the
         * floor was charged instead. Worth surfacing rather than hiding: it
         * is the reason a two-minute trip does not cost eleven rupees, and
         * the question a rider asks.
         */
        boolean minimumFareApplied
) {
}
