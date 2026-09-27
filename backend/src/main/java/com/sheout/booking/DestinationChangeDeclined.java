package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The partner said no to a change of destination. Nothing about the trip
 * changed: it goes on to the drop it was booked to, at the fare it was booked
 * at. Published so the rider is told plainly rather than left waiting.
 */
public class DestinationChangeDeclined extends DomainEvent {

    private final UUID changeId;
    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;
    private final GeoAddress drop;
    private final BigDecimal fare;

    public DestinationChangeDeclined(UUID changeId, UUID bookingId, UUID customerId, UUID driverId,
                                     GeoAddress drop, BigDecimal fare) {
        this.changeId = changeId;
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.drop = drop;
        this.fare = fare;
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

    /** Where the trip is still going. */
    public GeoAddress drop() {
        return drop;
    }

    /** What it still costs. */
    public BigDecimal fare() {
        return fare;
    }
}
