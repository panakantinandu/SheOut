package com.sheout.booking.internal.fare;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.GeoAddress;

import java.math.BigDecimal;

/**
 * The swap point for pricing. BookingService depends only on this
 * interface - adding surge pricing, real routing distance, or promo
 * discounts later means changing (or replacing) the implementation, never
 * touching BookingStateMachine or the request/complete flow that calls this.
 */
public interface FareCalculator {

    /**
     * The fare and the distance it was derived from. The quote endpoint
     * needs both; booking creation needs only the amount, which is why
     * estimate() below delegates here rather than the two being separate
     * calculations that could disagree.
     */
    FareQuote quote(BookingCategory category, GeoAddress pickup, GeoAddress drop);

    default BigDecimal estimate(BookingCategory category, GeoAddress pickup, GeoAddress drop) {
        return quote(category, pickup, drop).amount();
    }
}
