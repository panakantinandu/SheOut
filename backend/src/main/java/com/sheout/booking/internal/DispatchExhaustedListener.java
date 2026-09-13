package com.sheout.booking.internal;

import com.sheout.booking.BookingError;
import com.sheout.booking.BookingSummary;
import com.sheout.dispatch.DispatchExhausted;
import com.sheout.sharedkernel.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends a booking whose search found nobody.
 * <p>
 * Dispatch reports that it stopped looking; what that means for a booking
 * is decided here, in the module that owns booking state. Dispatch never
 * sets a status itself, the same way it never sets one when a driver
 * accepts - it calls assignDriver and lets booking run its own state
 * machine.
 * <p>
 * A losing race is expected here and is not an error. Between dispatch
 * giving up and this listener running, the rider may have cancelled, or a
 * driver holding a still-live offer may have accepted. Either leaves the
 * booking somewhere the state machine will not move to
 * NO_DRIVERS_AVAILABLE from, and the right answer in both cases is to
 * leave it alone: a rider who has already been told a partner is on the
 * way must not then be told nobody was found.
 */
@Component
class DispatchExhaustedListener {

    private static final Logger log = LoggerFactory.getLogger(DispatchExhaustedListener.class);

    private final BookingService bookingService;

    DispatchExhaustedListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @EventListener
    @Transactional
    public void onDispatchExhausted(DispatchExhausted event) {
        Result<BookingSummary, BookingError> result = bookingService.markNoDriversAvailable(event.bookingId());
        if (result.isFailure()) {
            // INVALID_STATE_TRANSITION here means the booking moved on while
            // the search was being wound up, which is the race above and is
            // fine. Logged at debug because it is normal, not a fault.
            log.debug("Booking {} was not left in REQUESTED when dispatch gave up ({}) - leaving it as it is",
                    event.bookingId(), result.error());
            return;
        }
        log.info("Booking {} ended as NO_DRIVERS_AVAILABLE after {} rounds over {}s ({})",
                event.bookingId(), event.roundsRun(), event.searchedFor().toSeconds(), event.reason());
    }
}
