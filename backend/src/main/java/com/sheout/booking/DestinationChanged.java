package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A trip's drop was changed mid-trip, by the rider's request and with her
 * partner's agreement. The booking already carries the new drop and fare when
 * this is published.
 * <p>
 * Its own event, not a variant of any lifecycle one: the trip's status does
 * not change - it is IN_PROGRESS before and after - and a subscriber to
 * BookingCompleted or BookingStarted must never mistake this for either.
 */
public class DestinationChanged extends DomainEvent {

    private final UUID changeId;
    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;
    private final GeoAddress oldDrop;
    private final GeoAddress newDrop;
    private final BigDecimal oldFare;
    private final BigDecimal newFare;

    public DestinationChanged(UUID changeId, UUID bookingId, UUID customerId, UUID driverId,
                              GeoAddress oldDrop, GeoAddress newDrop, BigDecimal oldFare, BigDecimal newFare) {
        this.changeId = changeId;
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.oldDrop = oldDrop;
        this.newDrop = newDrop;
        this.oldFare = oldFare;
        this.newFare = newFare;
    }

    public UUID changeId() {
        return changeId;
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

    public GeoAddress oldDrop() {
        return oldDrop;
    }

    public GeoAddress newDrop() {
        return newDrop;
    }

    public BigDecimal oldFare() {
        return oldFare;
    }

    public BigDecimal newFare() {
        return newFare;
    }
}
