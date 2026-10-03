package com.sheout.payments.internal.tax;

import com.sheout.booking.BookingCategory;

/**
 * What a supply is, for GST: each has its own rate and SAC code, from
 * configuration. App-booked bike taxis, autos, cabs and parcel delivery are
 * taxed differently, and so is SheOut's own fee and a seller's listing fee -
 * which is why nothing here carries a rate of its own.
 * <p>
 * PLATFORM_FEE is SheOut's commission as its own supply. It is configured
 * (and required) so that the answer to "is SheOut the supplier of the ride
 * under Section 9(5), or only of its platform service" can change by
 * configuration once the CA has given it; no invoice uses it today.
 */
public enum TaxCategory {
    BIKE,
    AUTO,
    CAB,
    PARCEL,
    PLATFORM_FEE,
    SELLER_LISTING_FEE;

    /** A trip's category. Lunch Box, not offered in the apps, is taxed as a parcel until the CA says otherwise. */
    public static TaxCategory forTrip(BookingCategory category) {
        return switch (category) {
            case BIKE -> BIKE;
            case AUTO -> AUTO;
            case CAB -> CAB;
            case PARCEL, LUNCHBOX -> PARCEL;
        };
    }
}
