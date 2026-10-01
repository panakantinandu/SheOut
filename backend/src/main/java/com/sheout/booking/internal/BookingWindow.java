package com.sheout.booking.internal;

import com.sheout.booking.ServiceHoursApi;

import java.time.Instant;
import java.util.Optional;

/**
 * What requestBooking needs to know about the service hours: whether new
 * bookings are taken right now, and the latest a trip booked now should be
 * finished by. Its own small interface so booking's tests can say "open" or
 * "closed" without standing up the whole hours service.
 */
interface BookingWindow {

    boolean open();

    /**
     * Closing time plus the grace allowed for the last trips, while daily
     * hours apply; empty when SheOut is open at all hours.
     */
    Optional<Instant> finishBy();

    static BookingWindow alwaysOpen() {
        return fixed(true, null);
    }

    static BookingWindow fixed(boolean open, Instant finishBy) {
        return new BookingWindow() {
            @Override
            public boolean open() {
                return open;
            }

            @Override
            public Optional<Instant> finishBy() {
                return Optional.ofNullable(finishBy);
            }
        };
    }

    static BookingWindow from(ServiceHoursApi hours) {
        return new BookingWindow() {
            @Override
            public boolean open() {
                return hours.currentStatus().open();
            }

            @Override
            public Optional<Instant> finishBy() {
                return hours.latestTripFinish();
            }
        };
    }
}
