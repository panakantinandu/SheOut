package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * The search ended with nobody found and the booking is closed as
 * NO_DRIVERS_AVAILABLE. Not a cancellation - nobody decided anything - but
 * like one, the trip will not happen: anything held for it (a promotional
 * credit) is given back.
 */
public class BookingNoDriversAvailable extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;

    public BookingNoDriversAvailable(UUID bookingId, UUID customerId) {
        this.bookingId = bookingId;
        this.customerId = customerId;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public UUID customerId() {
        return customerId;
    }
}
