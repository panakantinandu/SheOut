package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/** The partner could not hand a parcel over. The sender is asked to choose: wait, leave it with someone, or bring it back. */
public class ParcelDeliveryExceptionRaised extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;

    public ParcelDeliveryExceptionRaised(UUID bookingId, UUID customerId, UUID driverId) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public UUID customerId() {
        return customerId;
    }

    public UUID driverId() {
        return driverId;
    }
}
