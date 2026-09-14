package com.sheout.payments.internal;

import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentCaptured;
import com.sheout.payments.internal.gateway.GatewayPayment;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sheout.payments.PaymentError;
import com.sheout.payments.PaymentMethod;
import com.sheout.payments.PaymentStatus;
import com.sheout.payments.PaymentSummary;
import com.sheout.payments.internal.gateway.GatewayOrder;
import com.sheout.payments.internal.gateway.PaymentGateway;
import com.sheout.payments.PaymentStatus;
import com.sheout.sharedkernel.Result;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
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
    private final PlatformCommission platformCommission;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate transactions;

    public PaymentService(PaymentRepository paymentRepository, PaymentGateway paymentGateway,
                          PlatformCommission platformCommission, DomainEventPublisher eventPublisher,
                          PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.platformCommission = platformCommission;
        this.eventPublisher = eventPublisher;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * The one way a payment becomes CAPTURED, whichever path got it here -
     * Checkout, webhook, or cash. The caller holds the row lock and has
     * checked the payment is not already captured.
     * <p>
     * PaymentCaptured is published inside the same transaction, so whatever
     * reacts to it (the partner's wallet) commits or rolls back with the
     * capture. Two ways to pay must never mean two answers to what a partner
     * earned, so the settlement is recorded here, once.
     */
    private PaymentSummary markCaptured(PaymentEntity payment, PaymentMethod method, String razorpayPaymentId) {
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAt(Instant.now());
        payment.setFailureReason(null);
        if (razorpayPaymentId != null) {
            payment.setRazorpayPaymentId(razorpayPaymentId);
        }
        // The rider paid the full fare; this records what of it is the
        // partner's, and at what rate. Both numbers stay on the row rather
        // than the margin being folded into the price - see
        // PlatformCommission.
        payment.recordSettlement(platformCommission.payoutFrom(payment.getAmount()), platformCommission.percent());
        paymentRepository.save(payment);
        eventPublisher.publish(new PaymentCaptured(
                payment.getId(), payment.getBookingId(), method, payment.getAmount(),
                payment.getDriverPayout(), payment.getCommissionPercent(), payment.getCapturedAt()));
        return toSummary(payment);
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
        // Locked: the rider paying online and the partner confirming cash can
        // land at the same moment, and exactly one of them may capture.
        Optional<PaymentEntity> found = paymentRepository.findLockedByBookingId(bookingId);
        if (found.isEmpty()) {
            return Result.failure(PaymentError.PAYMENT_NOT_FOUND);
        }
        PaymentEntity payment = found.get();
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            // Idempotent guard, not an error path a normal caller should hit repeatedly:
            // never let a second "cash collected" call re-capture an already-settled payment.
            return Result.failure(PaymentError.ALREADY_CAPTURED);
        }
        return Result.success(markCaptured(payment, PaymentMethod.CASH, null));
    }

    /**
     * What the rider's browser needs to open Razorpay Checkout for this
     * booking's payment.
     * <p>
     * Normally the order already exists - it is created when the trip
     * completes. If that creation failed (Razorpay unreachable at the time),
     * it is created now, so a gateway blip at completion does not leave the
     * rider unable to pay online. The gateway call runs with no transaction
     * open, same rule as BookingCompletedListener.
     */
    public Result<CheckoutDetails, PaymentError> prepareCheckout(UUID bookingId) {
        Optional<PaymentEntity> found = paymentRepository.findByBookingId(bookingId);
        if (found.isEmpty()) {
            return Result.failure(PaymentError.PAYMENT_NOT_FOUND);
        }
        PaymentEntity payment = found.get();
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return Result.failure(PaymentError.ALREADY_CAPTURED);
        }
        String orderId = payment.getRazorpayOrderId();
        if (orderId == null) {
            Result<GatewayOrder, PaymentError> created = paymentGateway.createOrder(bookingId, payment.getAmount());
            if (created.isFailure()) {
                return Result.failure(created.error());
            }
            orderId = created.value().orderId();
            String newOrderId = orderId;
            transactions.executeWithoutResult(status -> paymentRepository.findLockedByBookingId(bookingId).ifPresent(p -> {
                p.setRazorpayOrderId(newOrderId);
                if (p.getStatus() == PaymentStatus.FAILED) {
                    p.setStatus(PaymentStatus.PENDING);
                    p.setFailureReason(null);
                }
                paymentRepository.save(p);
            }));
        }
        long paise = payment.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        return Result.success(new CheckoutDetails(paymentGateway.publicKeyId(), orderId, paise, "INR"));
    }

    /**
     * Rider's browser reports Checkout success. Nothing is trusted from it
     * beyond the ids: the signature must verify under our key secret, and
     * Razorpay itself must say the payment is captured (capturing it if it
     * is only authorised) for exactly this order and amount. Only then is
     * the payment marked CAPTURED.
     * <p>
     * The two Razorpay calls run with no transaction open; the capture is its
     * own short locked transaction. If the webhook got there first, this is
     * a no-op that reports the payment as it now stands.
     */
    public Result<PaymentSummary, PaymentError> confirmCheckout(UUID bookingId, String orderId, String razorpayPaymentId,
                                                               String signature) {
        Optional<PaymentEntity> found = paymentRepository.findByBookingId(bookingId);
        if (found.isEmpty() || !orderId.equals(found.get().getRazorpayOrderId())) {
            return Result.failure(PaymentError.PAYMENT_NOT_FOUND);
        }
        if (!paymentGateway.verifyCheckoutSignature(orderId, razorpayPaymentId, signature)) {
            log.warn("Checkout signature did not verify for booking {}", bookingId);
            return Result.failure(PaymentError.SIGNATURE_INVALID);
        }
        Result<GatewayPayment, PaymentError> confirmed =
                paymentGateway.confirmCapture(razorpayPaymentId, orderId, found.get().getAmount());
        if (confirmed.isFailure()) {
            return Result.failure(confirmed.error());
        }
        return transactions.execute(status -> {
            PaymentEntity payment = paymentRepository.findLockedByBookingId(bookingId).orElseThrow();
            if (payment.getStatus() == PaymentStatus.CAPTURED) {
                return Result.<PaymentSummary, PaymentError>success(toSummary(payment));
            }
            return Result.<PaymentSummary, PaymentError>success(
                    markCaptured(payment, confirmed.value().method(), confirmed.value().paymentId()));
        });
    }

    /** What Checkout.js is opened with. keyId is Razorpay's publishable key, not a secret. */
    public record CheckoutDetails(String keyId, String orderId, long amountPaise, String currency) {
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
            log.error("Razorpay order creation failed for payment {} - {}", paymentId, orderResult.error());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Razorpay order creation failed");
        }
        paymentRepository.save(payment);
    }

    /** Called by RazorpayWebhookController after signature verification. */
    @Transactional
    public void applyWebhookUpdate(String razorpayOrderId, String razorpayPaymentId, boolean captured, String failureReason,
                                   PaymentMethod method) {
        Optional<PaymentEntity> found = paymentRepository.findLockedByRazorpayOrderId(razorpayOrderId);
        if (found.isEmpty()) {
            log.warn("Razorpay webhook for unknown order id {}", razorpayOrderId);
            return;
        }
        PaymentEntity payment = found.get();
        // CAPTURED is final: a duplicate delivery must never re-apply. FAILED
        // is not. Razorpay lets a rider retry inside the same Checkout, so
        // payment.failed for her first attempt can be followed by
        // payment.captured for her second - ignoring that would leave a paid
        // trip reading as failed and the partner uncredited.
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return;
        }
        if (captured) {
            markCaptured(payment, method, razorpayPaymentId);
            return;
        }
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(failureReason);
        paymentRepository.save(payment);
    }

    /**
     * A page of the caller's payments. bookingIds is their ownership scope,
     * resolved from the token by the controller.
     *
     * This replaces what the customer app was doing: fetching every booking
     * and then one payment request per booking, discarding the ones with no
     * payment row. That was an N+1 on the client, it could not be filtered
     * or paged at all, and it grew with a customer's whole history.
     */
    public Page<PaymentSummary> pageForBookings(
            Collection<UUID> bookingIds,
            PaymentStatus status,
            Instant from,
            Instant to,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        if (bookingIds.isEmpty()) {
            // An empty IN clause is not valid SQL, and a customer with no
            // bookings has no payments - answer that directly.
            return Page.empty(pageable);
        }
        Pageable sorted = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return paymentRepository
                .findAll(PaymentSpecs.matching(bookingIds, status, from, to, minAmount, maxAmount), sorted)
                .map(this::toSummary);
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
                payment.getDriverPayout(),
                payment.getCommissionPercent(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getCapturedAt()
        );
    }
}
