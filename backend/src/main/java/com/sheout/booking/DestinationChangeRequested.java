package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A rider, mid-trip, has asked her partner to take her somewhere else. Nothing
 * about the trip has changed yet: this is the question, and the partner has
 * until expiresAt to answer it. Published so the partner is told at once -
 * she is riding, not watching her screen.
 */
public class DestinationChangeRequested extends DomainEvent {

    private final UUID changeId;
    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;
    private final GeoAddress newDrop;
    private final BigDecimal oldFare;
    private final BigDecimal newFare;
    private final Instant expiresAt;

    public DestinationChangeRequested(UUID changeId, UUID bookingId, UUID customerId, UUID driverId,
                                      GeoAddress newDrop, BigDecimal oldFare, BigDecimal newFare, Instant expiresAt) {
        this.changeId = changeId;
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.newDrop = newDrop;
        this.oldFare = oldFare;
        this.newFare = newFare;
        this.expiresAt = expiresAt;
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

    public GeoAddress newDrop() {
        return newDrop;
    }

    public BigDecimal oldFare() {
        return oldFare;
    }

    public BigDecimal newFare() {
        return newFare;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
