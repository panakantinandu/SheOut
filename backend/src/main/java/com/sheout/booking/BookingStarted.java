package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * A trip is underway: the partner is at the pickup, the rider is in the
 * vehicle, and ACCEPTED has become IN_PROGRESS.
 * <p>
 * This is the event a subscriber wants for "the trip actually started",
 * distinct from {@link BookingAccepted}, which only means a partner took the
 * job and set off. Nothing subscribes to it yet; it is published so that
 * when notifications, payments or anything else needs to react to a pickup,
 * it can subscribe rather than booking being taught to call it.
 * <p>
 * {@code pickupVerified} says whether a pickup code was actually checked.
 * It is false only for bookings accepted before pickup verification existed,
 * which were allowed to start unverified rather than stranding partners
 * mid-job at the deploy - a closed and shrinking set. A subscriber that
 * cares whether the rider was provably present must read this rather than
 * assume the event implies it.
 */
public class BookingStarted extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;
    private final boolean pickupVerified;
    private final BookingCategory category;
    private final GeoAddress pickup;
    private final GeoAddress drop;

    public BookingStarted(UUID bookingId, UUID customerId, UUID driverId, boolean pickupVerified) {
        this(bookingId, customerId, driverId, pickupVerified, null, null, null);
    }

    /**
     * With what the trip is and where it goes - for insurance, which opens
     * the trip's cover from this event and must not call back into booking
     * to find out (booking already asks insurance whether rides are covered).
     */
    public BookingStarted(UUID bookingId, UUID customerId, UUID driverId, boolean pickupVerified,
                          BookingCategory category, GeoAddress pickup, GeoAddress drop) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.pickupVerified = pickupVerified;
        this.category = category;
        this.pickup = pickup;
        this.drop = drop;
    }

    /** Null only from the old four-argument constructor. */
    public BookingCategory category() {
        return category;
    }

    public GeoAddress pickup() {
        return pickup;
    }

    public GeoAddress drop() {
        return drop;
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

    /** False only for trips that predate pickup verification - see this class's Javadoc. */
    public boolean pickupVerified() {
        return pickupVerified;
    }
}
