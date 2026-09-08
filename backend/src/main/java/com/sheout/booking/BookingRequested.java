package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * Published when a booking is created (REQUESTED). This is what dispatch
 * (not built yet) is expected to subscribe to in order to start matching -
 * booking itself does no matching.
 * <p>
 * ASSUMPTION FLAGGED on event payloads generally: exact fields weren't
 * specified for any of the 6 events in this module (unlike e.g.
 * AccountVerified(accountId, role), given explicitly in an earlier pass).
 * Kept minimal and purpose-specific, following that same precedent, rather
 * than embedding the full BookingSummary - pickup/category/type are what a
 * matcher would need; a listener wanting more can call BookingApi... except
 * BookingApi doesn't expose a read method today (see its Javadoc), so a
 * future dispatch module may need one added, the same way this pass added
 * findByAccountId to VerificationApi's predecessor state, if it turns out
 * to need one.
 */
public class BookingRequested extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final BookingType type;
    private final BookingCategory category;
    private final GeoAddress pickup;
    private final GeoAddress drop;

    public BookingRequested(UUID bookingId, UUID customerId, BookingType type, BookingCategory category,
                             GeoAddress pickup, GeoAddress drop) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.type = type;
        this.category = category;
        this.pickup = pickup;
        this.drop = drop;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public UUID customerId() {
        return customerId;
    }

    public BookingType type() {
        return type;
    }

    public BookingCategory category() {
        return category;
    }

    public GeoAddress pickup() {
        return pickup;
    }

    public GeoAddress drop() {
        return drop;
    }
}
