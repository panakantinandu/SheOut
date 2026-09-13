package com.sheout.ratings.internal;

import com.sheout.booking.BookingCompleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Opens the two rating slots when a trip ends.
 * <p>
 * Reacts to the event rather than booking calling into ratings - booking has
 * no idea this module exists, the same convention dispatch, payments and
 * notifications all follow.
 * <p>
 * Named for what it does rather than for the event it listens to, because
 * payments already has a BookingCompletedListener and Spring names a
 * component from its simple class name regardless of package: two classes
 * called the same thing in different modules refuse to start the
 * application. Worth knowing before adding a third listener to this event.
 * <p>
 * A plain @EventListener inside booking's own transaction, deliberately
 * unlike payments' BookingCompletedListener next door, which runs
 * AFTER_COMMIT. That one has to, because it makes an HTTP call to a payment
 * gateway and must never hold a database transaction open across it, or risk
 * booking's commit if the gateway is slow. This does two local inserts and
 * nothing else, and they should live or die with the completion itself: a
 * trip that completed must have slots, and a completion that rolled back
 * must not leave slots for a trip that never ended. Same reasoning as
 * users' profile listeners.
 */
@Component
class RatingSlotOpener {

    private final RatingService ratingService;

    RatingSlotOpener(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @EventListener
    @Transactional
    public void onBookingCompleted(BookingCompleted event) {
        // BookingCompleted does not carry the completion timestamp, and this
        // runs inside the transaction that set it, so now() is the same
        // instant to within the time it took to publish. Widening the event
        // for a millisecond of precision would not buy anything a rating
        // window cares about.
        ratingService.openSlotsFor(event.bookingId(), event.customerId(), event.driverId(), Instant.now());
    }
}
