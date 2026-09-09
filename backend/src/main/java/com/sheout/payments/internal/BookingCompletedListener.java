package com.sheout.payments.internal;

import com.sheout.booking.BookingCompleted;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to BookingCompleted rather than booking calling into payments
 * directly - booking has no idea payments exists, same convention as
 * DispatchService.onBookingRequested.
 * <p>
 * ASSUMPTION FLAGGED / deliberate deviation from that same precedent:
 * this uses @TransactionalEventListener(AFTER_COMMIT) instead of a plain
 * @EventListener. BookingService.completeTrip publishes BookingCompleted
 * from inside its own @Transactional method; a plain @EventListener would
 * run synchronously inside that same still-open transaction, meaning a
 * Razorpay HTTP call (which onBookingCompleted makes) would block a DB
 * transaction on an external network call, and a slow/failing gateway
 * call would risk booking's own commit. AFTER_COMMIT runs only once
 * booking's transaction has actually committed, and a failure here can
 * never roll booking's completion back.
 */
@Component
class BookingCompletedListener {

    private final PaymentService paymentService;

    BookingCompletedListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCompleted(BookingCompleted event) {
        paymentService.onBookingCompleted(event.bookingId(), event.finalFare());
    }
}
