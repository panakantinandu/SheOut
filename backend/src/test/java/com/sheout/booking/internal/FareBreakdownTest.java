package com.sheout.booking.internal;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingType;
import com.sheout.booking.GeoAddress;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Her receipt's breakdown is the quote the trip was priced on, and is
 * dropped - not left stale - when a destination change re-prices the trip.
 * Nothing about insurance is in it: there is no field for one.
 */
class FareBreakdownTest {

    private BookingEntity booking() {
        BookingEntity b = new BookingEntity(BookingType.RIDE, BookingCategory.BIKE, UUID.randomUUID(),
                GeoAddressEmbeddable.from(new GeoAddress("A", 17.41, 78.44)),
                GeoAddressEmbeddable.from(new GeoAddress("B", 17.44, 78.38)), new BigDecimal("96.00"));
        b.recordFareBreakdown(new BigDecimal("22.00"), new BigDecimal("54.00"), new BigDecimal("20.00"),
                BigDecimal.ONE, BigDecimal.ONE, false);
        return b;
    }

    @Test
    void theBreakdownIsTheQuoteTheTripWasPricedOn() {
        BookingEntity b = booking();

        assertThat(b.getFareBaseFare().add(b.getFareDistanceCharge()).add(b.getFareTimeCharge()))
                .isEqualByComparingTo(b.getFareEstimate());
        assertThat(b.getFareMinimumApplied()).isFalse();
    }

    @Test
    void aDestinationChangeDropsTheOldBreakdownRatherThanShowingOneThatNoLongerAddsUp() {
        BookingEntity b = booking();

        b.changeDestination(GeoAddressEmbeddable.from(new GeoAddress("C", 17.45, 78.36)), new BigDecimal("130.00"),
                new BigDecimal("9.10"), true, Instant.now());

        assertThat(b.getFareBaseFare()).isNull();
        assertThat(b.getFareDistanceCharge()).isNull();
        assertThat(b.getFareMinimumApplied()).isNull();
        assertThat(b.getFareEstimate()).isEqualByComparingTo("130.00");
    }
}
