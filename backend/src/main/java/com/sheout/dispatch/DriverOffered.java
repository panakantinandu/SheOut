package com.sheout.dispatch;

import com.sheout.booking.BookingCategory;
import com.sheout.sharedkernel.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Dispatch has offered a booking to one partner. Published once per partner
 * per round, after the offer exists in the offer store - so a partner
 * alerted by this can actually accept it.
 * <p>
 * Carries what an alert needs and nothing about the rider: no name, no
 * pickup address. A partner sees those once she opens the offer, and a lock
 * screen is not the place for where a woman is waiting.
 */
public class DriverOffered extends DomainEvent {

    private final UUID bookingId;
    private final UUID driverId;
    private final BookingCategory category;
    private final double distanceKm;
    private final Instant expiresAt;

    public DriverOffered(UUID bookingId, UUID driverId, BookingCategory category, double distanceKm, Instant expiresAt) {
        this.bookingId = bookingId;
        this.driverId = driverId;
        this.category = category;
        this.distanceKm = distanceKm;
        this.expiresAt = expiresAt;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public UUID driverId() {
        return driverId;
    }

    public BookingCategory category() {
        return category;
    }

    /** From her last reported position to the pickup, straight line. */
    public double distanceKm() {
        return distanceKm;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
