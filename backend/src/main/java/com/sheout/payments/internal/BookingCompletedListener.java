package com.sheout.payments.internal;

import com.sheout.booking.BookingCompleted;
import com.sheout.payments.PaymentError;
import com.sheout.payments.internal.gateway.GatewayOrder;
import com.sheout.payments.internal.gateway.PaymentGateway;
import com.sheout.sharedkernel.Result;
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
 * Razorpay HTTP call (which this triggers) would block a DB transaction on
 * an external network call, and a slow/failing gateway call would risk
 * booking's own commit. AFTER_COMMIT runs only once booking's transaction
 * has actually committed, and a failure here can never roll booking's
 * completion back.
 * <p>
 * The orchestration (create pending row -> call gateway with no open
 * transaction -> save the result) lives HERE rather than as one
 * PaymentService method, and both PaymentService calls go through the
 * injected proxy bean rather than one method calling the other via
 * {@code this.} - both matter. AFTER_COMMIT still runs with Spring
 * considering transaction synchronization "active" on this thread even
 * though the triggering transaction already committed, so a save reached
 * through anything other than a REQUIRES_NEW proxy call silently joins
 * that stale synchronization instead of opening a real one: it reports
 * success and returns a generated id, but nothing is ever actually
 * committed (confirmed directly against Postgres - see
 * PaymentService.createPendingPayment's Javadoc).
 */
@Component
class BookingCompletedListener {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    BookingCompletedListener(PaymentService paymentService, PaymentGateway paymentGateway) {
        this.paymentService = paymentService;
        this.paymentGateway = paymentGateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCompleted(BookingCompleted event) {
        PaymentEntity payment = paymentService.createPendingPayment(event.bookingId(), event.finalFare());
        if (payment == null) {
            return; // duplicate/retried event for a booking we've already recorded a payment for
        }
        Result<GatewayOrder, PaymentError> orderResult =
                paymentGateway.createOrder(event.bookingId(), event.finalFare());
        paymentService.applyGatewayResult(payment.getId(), orderResult);
    }
}
