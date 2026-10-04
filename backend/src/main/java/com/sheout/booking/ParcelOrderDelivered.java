package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * A parcel that named an order - which product it carried, and how many -
 * was delivered. Published with BookingCompleted, only for such a parcel.
 * The reference is opaque here: whoever made it (the marketplace) decides
 * what it means.
 */
public class ParcelOrderDelivered extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID orderProductId;
    private final int orderQuantity;

    public ParcelOrderDelivered(UUID bookingId, UUID customerId, UUID orderProductId, int orderQuantity) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.orderProductId = orderProductId;
        this.orderQuantity = orderQuantity;
    }

    public UUID bookingId() { return bookingId; }
    /** Whoever booked the parcel - the buyer or the seller. */
    public UUID customerId() { return customerId; }
    public UUID orderProductId() { return orderProductId; }
    public int orderQuantity() { return orderQuantity; }
}
