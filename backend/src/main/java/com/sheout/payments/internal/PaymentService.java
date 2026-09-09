package com.sheout.payments.internal;

import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentError;
import com.sheout.payments.PaymentMethod;
import com.sheout.payments.PaymentStatus;
import com.sheout.payments.PaymentSummary;
import com.sheout.payments.internal.gateway.GatewayOrder;
import com.sheout.payments.internal.gateway.PaymentGateway;
import com.sheout.sharedkernel.Result;
import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;

    public PaymentService(PaymentRepository paymentRepository, PaymentGateway paymentGateway, EntityManager entityManager) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.entityManager = entityManager;
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
        log.info("createPendingPayment called - bookingId: {}, finalFare: {}", bookingId, finalFare);
        if (paymentRepository.findByBookingId(bookingId).isPresent()) {
            log.warn("Payment already exists for bookingId: {}", bookingId);
            return null;
        }
        PaymentEntity payment = new PaymentEntity(bookingId, finalFare, PaymentMethod.UPI, PaymentStatus.PENDING);
        try {
            PaymentEntity saved = paymentRepository.save(payment);
            log.info("Payment created and saved - id: {}, bookingId: {}, orderId: {}", saved.getId(), saved.getBookingId(), saved.getRazorpayOrderId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Two BookingCompleted deliveries raced past the findByBookingId check above -
            // the unique constraint on booking_id is the real guard; losing this race is fine.
            log.warn("DataIntegrityViolationException - duplicate payment for bookingId: {}", bookingId);
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
        log.info("applyGatewayResult called - paymentId: {}, orderResult.success: {}", paymentId, orderResult.isSuccess());
        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow();
        if (orderResult.isSuccess()) {
            String orderId = orderResult.value().orderId();
            log.info("Setting Razorpay order ID: {} on payment: {}", orderId, paymentId);
            payment.setRazorpayOrderId(orderId);
        } else {
            log.error("Gateway order creation failed: {}", orderResult.error());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Razorpay order creation failed");
        }
        log.info("Saving payment with orderId: {}", payment.getRazorpayOrderId());
        paymentRepository.save(payment);
        paymentRepository.flush();  // Force immediate database flush
        entityManager.clear();      // Clear session cache to force fresh database reads
        log.info("Payment saved, flushed, and session cleared - verifying: {}", paymentRepository.findById(paymentId).map(p -> p.getRazorpayOrderId()).orElse("NOT FOUND"));
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
