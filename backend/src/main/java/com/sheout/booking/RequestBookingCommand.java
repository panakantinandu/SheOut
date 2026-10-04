package com.sheout.booking;

import java.util.UUID;

/**
 * @param parcelNote for a delivery only: what is in the parcel, in the
 *                   sender's words ("Marketplace order 7K9M2XQ: silk saree"),
 *                   shown to the partner who carries it. Null for a ride.
 */
public record RequestBookingCommand(
        UUID customerId,
        BookingType type,
        BookingCategory category,
        GeoAddress pickup,
        GeoAddress drop,
        String parcelNote
) {

    /** A booking with no parcel note - every ride, and a parcel sent without one. */
    public RequestBookingCommand(UUID customerId, BookingType type, BookingCategory category, GeoAddress pickup,
                                 GeoAddress drop) {
        this(customerId, type, category, pickup, drop, null);
    }
}
