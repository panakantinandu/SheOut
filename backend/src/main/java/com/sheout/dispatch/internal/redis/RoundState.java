package com.sheout.dispatch.internal.redis;

import com.sheout.booking.BookingCategory;

/**
 * Public - read by DispatchService (com.sheout.dispatch.internal) across
 * the sub-package boundary.
 * <p>
 * Carries pickup/category alongside the retry bookkeeping (attempt,
 * radiusKm) because BookingApi has no read method (only requestBooking/
 * assignDriver) - a retry round has nothing else to re-fetch this from,
 * so dispatch captures it itself from the original BookingRequested event
 * and re-persists it on every round.
 */
public record RoundState(int attempt, double radiusKm, double pickupLat, double pickupLng, BookingCategory category) {
}
