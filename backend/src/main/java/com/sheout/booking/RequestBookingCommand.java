package com.sheout.booking;

import java.util.UUID;

/**
 * @param parcelNote     for a delivery only: what is in the parcel, in the
 *                       sender's words ("Marketplace order 7K9M2XQ: silk saree"),
 *                       shown to the partner who carries it. Null for a ride.
 * @param orderProductId for a delivery only: the order it carries, as an
 *                       opaque reference (a Marketplace product) - see
 *                       ParcelOrderDelivered. Null for anything else.
 * @param orderQuantity  how many of it, 1 to 20; null with no order.
 * @param parcel         for a delivery: who it is for, and what the sender agreed to. Required for a delivery.
 */
public record RequestBookingCommand(
        UUID customerId,
        BookingType type,
        BookingCategory category,
        GeoAddress pickup,
        GeoAddress drop,
        String parcelNote,
        UUID orderProductId,
        Integer orderQuantity,
        ParcelDetails parcel
) {

    public RequestBookingCommand(UUID customerId, BookingType type, BookingCategory category, GeoAddress pickup,
                                 GeoAddress drop, String parcelNote, UUID orderProductId, Integer orderQuantity) {
        this(customerId, type, category, pickup, drop, parcelNote, orderProductId, orderQuantity, null);
    }

    /** A booking with no parcel note - every ride, and a parcel sent without one. */
    public RequestBookingCommand(UUID customerId, BookingType type, BookingCategory category, GeoAddress pickup,
                                 GeoAddress drop) {
        this(customerId, type, category, pickup, drop, null, null, null, null);
    }

    public RequestBookingCommand(UUID customerId, BookingType type, BookingCategory category, GeoAddress pickup,
                                 GeoAddress drop, String parcelNote) {
        this(customerId, type, category, pickup, drop, parcelNote, null, null, null);
    }
}
