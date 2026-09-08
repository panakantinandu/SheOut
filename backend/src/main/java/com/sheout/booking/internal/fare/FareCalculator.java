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

    BigDecimal estimate(BookingCategory category, GeoAddress pickup, GeoAddress drop);
}
