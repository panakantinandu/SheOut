package com.sheout.booking.internal.fare;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.internal.GeoDistance;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Placeholder pricing: base fare + (straight-line distance x per-km rate).
 * <p>
 * FLAGGED: straight-line (haversine) distance, not real routing distance -
 * noted in the interface Javadoc too, this is a future swap once a routing
 * provider is picked. The base/per-km rate numbers below are also
 * illustrative, not from a real pricing spec - loosely reverse-engineered
 * from the one data point in the UI mockup (Bike Taxi, 2.8 km, ~₹42-58) so
 * BIKE lands in a plausible range; the other categories' rates are guesses
 * scaled relative to that, not measured against anything real. Expect to
 * replace all of these.
 */
@Component
public class DistanceBasedFareCalculator implements FareCalculator {

    private record Rate(BigDecimal base, BigDecimal perKm) {
    }

    private static final java.util.Map<BookingCategory, Rate> RATES = java.util.Map.of(
            BookingCategory.BIKE, new Rate(new BigDecimal("20.00"), new BigDecimal("8.00")),
            BookingCategory.AUTO, new Rate(new BigDecimal("30.00"), new BigDecimal("12.00")),
            BookingCategory.CAB, new Rate(new BigDecimal("50.00"), new BigDecimal("18.00")),
            BookingCategory.PARCEL, new Rate(new BigDecimal("25.00"), new BigDecimal("10.00")),
            BookingCategory.LUNCHBOX, new Rate(new BigDecimal("15.00"), new BigDecimal("6.00"))
    );

    @Override
    public FareQuote quote(BookingCategory category, GeoAddress pickup, GeoAddress drop) {
        double distanceKm = GeoDistance.haversineKm(pickup, drop);
        Rate rate = RATES.get(category);
        BigDecimal amount = rate.base()
                .add(rate.perKm().multiply(BigDecimal.valueOf(distanceKm)))
                .setScale(2, RoundingMode.HALF_UP);
        return new FareQuote(amount, distanceKm);
    }

}
