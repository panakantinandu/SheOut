package com.sheout.payments.internal;

import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentError;
import com.sheout.payments.PaymentMethod;
import com.sheout.payments.PaymentStatus;
import com.sheout.payments.PaymentSummary;
import com.sheout.payments.internal.gateway.GatewayOrder;
import com.sheout.payments.internal.gateway.PaymentGateway;
import com.sheout.sharedkernel.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService implements PaymentApi {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentRepository paymentRepository, PaymentGateway paymentGateway) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    @Override
    public Result<PaymentSummary, PaymentError> getPaymentStatus(UUID bookingId) {
        return paymentRepository.findByBookingId(bookingId)
                .map(payment -> Result.<PaymentSummary, PaymentError>success(toSummary(payment)))
                .orElseGet(() -> Result.failure(PaymentError.PAYMENT_NOT_FOUND));
    }

    /**
     * See PaymentApi's Javadoc for why this requires a payment row to
     * already exist (created by createPendingPayment, via
     * BookingCompletedListener) rather than accepting an amount itself.
     */
    @Override
    @Transactional
    public Result<PaymentSummary, PaymentError> initiateCashPayment(UUID bookingId) {
        Optional<PaymentEntity> found = paymentRepository.findByBookingId(bookingId);
        if (found.isEmpty()) {
            return Result.failure(PaymentError.PAYMENT_NOT_FOUND);
        }
        PaymentEntity payment = found.get();
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            // Idempotent guard, not an error path a normal caller should hit repeatedly:
            // never let a second "cash collected" call re-capture an already-settled payment.
            return Result.failure(PaymentError.ALREADY_CAPTURED);
        }
        payment.setMethod(PaymentMethod.CASH);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAt(Instant.now());
        payment.setFailureReason(null);
        paymentRepository.save(payment);
        return Result.success(toSummary(payment));
    }

    /**
     * Called by BookingCompletedListener via its injected PaymentService
     * bean (never self-invoked), which matters here: this runs from an
     * AFTER_COMMIT transaction-synchronization callback, where Spring still
     * considers synchronization "active" on the thread even though the
     * triggering transaction has already committed. A plain default-
     * propagation @Transactional (or no annotation at all) join()s that
     * stale synchronization instead of opening a real one - the save
     * reports success and returns a generated id, but nothing is ever
     * actually committed (confirmed directly against Postgres: the row
     * never exists, even though this method returns normally with a real
     * id). REQUIRES_NEW forces a genuinely fresh, independently-committed
     * transaction, which only takes effect when called through the Spring
     * proxy - i.e. only when the caller holds an injected PaymentService,
     * not via {@code this.}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentEntity createPendingPayment(UUID bookingId, BigDecimal finalFare) {
        if (paymentRepository.findByBookingId(bookingId).isPresent()) {
            return null;
        }
        PaymentEntity payment = new PaymentEntity(bookingId, finalFare, PaymentMethod.UPI, PaymentStatus.PENDING);
        try {
            return paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            // Two BookingCompleted deliveries raced past the findByBookingId check above -
            // the unique constraint on booking_id is the real guard; losing this race is fine.
            return null;
        }
    }

    /**
     * The second half of BookingCompletedListener's flow, saved as its own
     * REQUIRES_NEW transaction for the same reason createPendingPayment is
     * - see its Javadoc. Kept as a separate call (not one @Transactional
     * method wrapping the gateway call too) so the Razorpay HTTP call itself
     * - which can take tens of seconds, see RazorpayPaymentGateway - never
     * runs with a DB transaction/connection held open.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyGatewayResult(UUID paymentId, Result<GatewayOrder, PaymentError> orderResult) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        if (orderResult.isSuccess()) {
            payment.setRazorpayOrderId(orderResult.value().orderId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Razorpay order creation failed");
        }
        paymentRepository.save(payment);
    }

    /** Called by RazorpayWebhookController after signature verification. */
    @Transactional
    public void applyWebhookUpdate(String razorpayOrderId, String razorpayPaymentId, boolean captured, String failureReason) {
        Optional<PaymentEntity> found = paymentRepository.findByRazorpayOrderId(razorpayOrderId);
        if (found.isEmpty()) {
            log.warn("Razorpay webhook for unknown order id {}", razorpayOrderId);
            return;
        }
        PaymentEntity payment = found.get();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return; // idempotent - a duplicate webhook delivery must never re-apply a second time
        }
        payment.setRazorpayPaymentId(razorpayPaymentId);
        if (captured) {
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setCapturedAt(Instant.now());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(failureReason);
        }
        paymentRepository.save(payment);
    }

    private PaymentSummary toSummary(PaymentEntity payment) {
        return new PaymentSummary(
                payment.getId(),
                payment.getBookingId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getRazorpayOrderId(),
                payment.getRazorpayPaymentId(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getCapturedAt()
        );
    }
}
