package com.sheout.booking.internal.fare;

import java.math.BigDecimal;

/**
 * What a fare works out to, and the distance it was worked out from.
 * <p>
 * The distance is already computed inside the calculator to produce the
 * amount, so returning both together is what lets the quote endpoint show
 * "(2.8 km)" without a second, separately-implemented distance function
 * that could drift from the one pricing actually uses.
 * <p>
 * distanceKm is straight-line, not routed - see
 * DistanceBasedFareCalculator's Javadoc. A customer reading "2.8 km" is
 * therefore reading the distance the fare was based on, not the distance
 * they will travel.
 */
public record FareQuote(BigDecimal amount, double distanceKm) {
}
