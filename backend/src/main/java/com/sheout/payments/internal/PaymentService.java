package com.sheout.payments.internal;

import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingSummary;
import com.sheout.payments.ListingFeeCheckout;
import com.sheout.payments.ListingFeePaid;
import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentPurpose;
import com.sheout.payments.UpiQr;
import com.sheout.payments.internal.gateway.GatewayQr;
import com.sheout.payments.internal.wallet.RiderWalletService;
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
import java.util.List;
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
    private final BookingApi bookingApi;
    private final RiderWalletService riderWalletService;

    public PaymentService(PaymentRepository paymentRepository, PaymentGateway paymentGateway,
                          PlatformCommission platformCommission, DomainEventPublisher eventPublisher,
                          PlatformTransactionManager transactionManager, BookingApi bookingApi,
                          RiderWalletService riderWalletService) {
        this.bookingApi = bookingApi;
        this.riderWalletService = riderWalletService;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.platformCommission = platformCommission;
        this.eventPublisher = eventPublisher;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * A trip a promotion paid for in full: there is nothing to charge, so it
     * is captured at once, as paid by promotional credit. The partner is
     * credited her whole share, exactly as for a trip the rider paid, and the
     * trip is settled so nobody is held for a zero fare.
     * <p>
     * REQUIRES_NEW for the same reason as createPendingPayment: it is called
     * from BookingCompletedListener after booking's commit, where a joined
     * transaction reports success and commits nothing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settleCoveredByPromotion(UUID bookingId) {
        paymentRepository.findLockedByBookingId(bookingId)
                .filter(payment -> !settled(payment))
                .ifPresent(payment -> markCaptured(payment, PaymentMethod.PROMO_CREDIT, null));
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
        if (payment.getPurpose() == PaymentPurpose.SELLER_LISTING_FEE) {
            // SheOut's own fee: nobody's share to settle, and nothing that
            // listens for a trip's capture should hear about it.
            paymentRepository.save(payment);
            eventPublisher.publish(new ListingFeePaid(payment.getId(), payment.getPayerAccountId(), payment.getSellerId(),
                    payment.getAmount(), method, payment.getCapturedAt()));
            return toSummary(payment);
        }
        // The rider paid the full fare; this records what of it is the
        // partner's, and at what rate. Both numbers stay on the row rather
        // than the margin being folded into the price - see
        // PlatformCommission.
        // From the whole fare, not what the rider paid: a promotion is
        // SheOut's cost, never taken out of the partner's share.
        payment.recordSettlement(platformCommission.payoutFrom(payment.getFareAmount()), platformCommission.percent());
        paymentRepository.save(payment);
        eventPublisher.publish(new PaymentCaptured(
                payment.getId(), payment.getBookingId(), method, payment.getAmount(),
                payment.getDriverPayout(), payment.getCommissionPercent(), payment.getCapturedAt()));
        // The trip is over now: the rider may book again and the partner is
        // free for her next offer. Same transaction as the capture. A refusal
        // here is logged, not thrown - money Razorpay has already taken must
        // never be un-recorded because of a booking-side problem.
        if (payment.getRazorpayQrId() != null) {
            // Paid - by the QR or another way while it was out. Either way it
            // must not take a second payment; closing a used one is harmless.
            String qrId = payment.getRazorpayQrId();
            afterCommit(() -> paymentGateway.closeQr(qrId));
        }
        Result<BookingSummary, BookingError> settled = bookingApi.markPaymentSettled(payment.getBookingId());
        if (settled.isFailure()) {
            log.error("Payment {} captured but booking {} could not be marked settled - {}",
                    payment.getId(), payment.getBookingId(), settled.error());
        }
        return toSummary(payment);
    }

    /**
     * The payment row for a trip that has ended, creating it if it is
     * missing.
     * <p>
     * It is normally written just after the trip ends, by
     * BookingCompletedListener. If that failed - the app restarted in the
     * gap, say - the trip would be ended, unpaid, and unpayable, and the rider
     * locked out of booking by a fare she has no way to pay. So every path
     * that needs the row makes sure it exists, from the booking's own final
     * fare. Empty only when the trip has not ended.
     */
    private Optional<PaymentEntity> ensurePayment(UUID bookingId) {
        Optional<PaymentEntity> existing = paymentRepository.findByBookingId(bookingId);
        if (existing.isPresent()) {
            return existing;
        }
        Optional<BookingSummary> booking = bookingApi.findById(bookingId)
                .filter(b -> b.status() == BookingStatus.COMPLETED && b.finalFare() != null);
        if (booking.isEmpty()) {
            return Optional.empty();
        }
        log.warn("Booking {} ended with no payment row - creating it now", bookingId);
        createPendingPayment(bookingId, booking.get().amountDue(), booking.get().finalFare());
        Optional<PaymentEntity> created = paymentRepository.findByBookingId(bookingId);
        // A trip already settled without a row is one V23 grandfathered.
        // Recording it as payable would offer the rider a "Pay" button for a
        // trip that is closed; it is recorded the way V23 recorded its
        // siblings instead.
        if (booking.get().paymentSettledAt() != null) {
            created.filter(p -> p.getStatus() == PaymentStatus.PENDING).ifPresent(p -> {
                p.setStatus(PaymentStatus.WAIVED);
                p.setFailureReason("Trip ended before payment was required to close it");
                paymentRepository.save(p);
            });
        }
        return created;
    }

    private static boolean settled(PaymentEntity payment) {
        return payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.WAIVED;
    }

    @Override
    public Result<PaymentSummary, PaymentError> getPaymentStatus(UUID bookingId) {
        return ensurePayment(bookingId)
                .map(payment -> Result.<PaymentSummary, PaymentError>success(toSummary(payment)))
                .orElseGet(() -> Result.failure(PaymentError.PAYMENT_NOT_FOUND));
    }

    /**
     * Pays a trip from the rider's SheOut wallet: her balance goes down, the
     * payment is captured, the partner is credited and the trip is settled -
     * all in one transaction, so none of it can happen without the rest.
     * <p>
     * The payment row is locked first and the wallet second, the only place
     * both are held, so there is no lock-order deadlock to hit. The caller
     * has checked she is the rider on this booking.
     */
    public Result<PaymentSummary, PaymentError> payFromWallet(UUID bookingId, UUID customerAccountId) {
        if (ensurePayment(bookingId).isEmpty()) {
            return Result.failure(PaymentError.TRIP_NOT_ENDED);
        }
        return transactions.execute(status -> {
            PaymentEntity payment = paymentRepository.findLockedByBookingId(bookingId).orElseThrow();
            if (settled(payment)) {
                return Result.<PaymentSummary, PaymentError>failure(PaymentError.ALREADY_CAPTURED);
            }
            Result<BigDecimal, PaymentError> debit =
                    riderWalletService.debitForTrip(customerAccountId, bookingId, payment.getAmount());
            if (debit.isFailure()) {
                status.setRollbackOnly();
                return Result.<PaymentSummary, PaymentError>failure(debit.error());
            }
            return Result.<PaymentSummary, PaymentError>success(
                    markCaptured(payment, PaymentMethod.SHEOUT_WALLET, null));
        });
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
        Optional<PaymentEntity> found = ensurePayment(bookingId);
        if (found.isEmpty()) {
            return Result.failure(PaymentError.TRIP_NOT_ENDED);
        }
        PaymentEntity payment = found.get();
        if (settled(payment)) {
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
            if (settled(payment)) {
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
        return createPendingPayment(bookingId, finalFare, finalFare);
    }

    /**
     * amountDue is what the rider is charged; finalFare the whole fare, which
     * the partner's share is worked out from. They differ only when a
     * promotion paid part of the trip.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentEntity createPendingPayment(UUID bookingId, BigDecimal amountDue, BigDecimal finalFare) {
        if (paymentRepository.findByBookingId(bookingId).isPresent()) {
            return null;
        }
        PaymentEntity payment = new PaymentEntity(bookingId, amountDue, PaymentMethod.UPI, PaymentStatus.PENDING);
        payment.setFareAmount(finalFare);
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

    /**
     * Called by RazorpayWebhookController after signature verification.
     * False when the order is not a trip's - it may be a wallet top-up's.
     */
    @Transactional
    public boolean applyWebhookUpdate(String razorpayOrderId, String razorpayPaymentId, boolean captured,
                                      String failureReason, PaymentMethod method) {
        Optional<PaymentEntity> found = paymentRepository.findLockedByRazorpayOrderId(razorpayOrderId);
        if (found.isEmpty()) {
            return false;
        }
        PaymentEntity payment = found.get();
        // CAPTURED is final: a duplicate delivery must never re-apply. FAILED
        // is not. Razorpay lets a rider retry inside the same Checkout, so
        // payment.failed for her first attempt can be followed by
        // payment.captured for her second - ignoring that would leave a paid
        // trip reading as failed and the partner uncredited.
        if (settled(payment)) {
            return true;
        }
        if (captured) {
            markCaptured(payment, method, razorpayPaymentId);
            return true;
        }
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(failureReason);
        paymentRepository.save(payment);
        return true;
    }

    /**
     * A page of the caller's payments. bookingIds and payerAccountId are
     * their ownership scope - her trips, and what she paid that is no trip's
     * (a listing fee) - both resolved from the token by the controller.
     *
     * This replaces what the customer app was doing: fetching every booking
     * and then one payment request per booking, discarding the ones with no
     * payment row. That was an N+1 on the client, it could not be filtered
     * or paged at all, and it grew with a customer's whole history.
     */
    public Page<PaymentSummary> pageForBookings(
            Collection<UUID> bookingIds,
            UUID payerAccountId,
            PaymentStatus status,
            Instant from,
            Instant to,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        if (bookingIds.isEmpty() && payerAccountId == null) {
            // Nothing is hers: answer that directly rather than query.
            return Page.empty(pageable);
        }
        Pageable sorted = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return paymentRepository
                .findAll(PaymentSpecs.matching(bookingIds, payerAccountId, status, from, to, minAmount, maxAmount), sorted)
                .map(this::toSummary);
    }

    // ------------------------------------------------------------ listing fees

    /**
     * The seller's listing fee order. The row is made once per seller; the
     * Razorpay order is made with no transaction open (it can take seconds),
     * then written back under the row lock, the same shape as
     * prepareCheckout for a trip.
     */
    @Override
    public Result<ListingFeeCheckout, PaymentError> startListingFeeCheckout(UUID payerAccountId, UUID sellerId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return Result.failure(PaymentError.INVALID_AMOUNT);
        }
        PaymentEntity payment = transactions.execute(status -> paymentRepository.findLockedBySellerId(sellerId)
                .orElseGet(() -> paymentRepository.save(PaymentEntity.listingFee(payerAccountId, sellerId, amount))));
        if (!payerAccountId.equals(payment.getPayerAccountId())) {
            return Result.failure(PaymentError.PAYMENT_NOT_FOUND);
        }
        if (settled(payment)) {
            return Result.failure(PaymentError.ALREADY_CAPTURED);
        }
        String orderId = payment.getRazorpayOrderId();
        if (orderId == null) {
            // The payment row's own id is the receipt: a listing fee has no booking.
            Result<GatewayOrder, PaymentError> created = paymentGateway.createOrder(payment.getId(), payment.getAmount());
            if (created.isFailure()) {
                return Result.failure(created.error());
            }
            orderId = created.value().orderId();
            String newOrderId = orderId;
            transactions.executeWithoutResult(status -> paymentRepository.findLockedBySellerId(sellerId).ifPresent(p -> {
                p.setRazorpayOrderId(newOrderId);
                paymentRepository.save(p);
            }));
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            // A declined card is not the end of it: she tries again on the same order.
            transactions.executeWithoutResult(status -> paymentRepository.findLockedBySellerId(sellerId).ifPresent(p -> {
                p.setStatus(PaymentStatus.PENDING);
                p.setFailureReason(null);
                paymentRepository.save(p);
            }));
        }
        long paise = payment.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        return Result.success(new ListingFeeCheckout(payment.getId(), paymentGateway.publicKeyId(), orderId, paise, "INR"));
    }

    @Override
    public Result<PaymentSummary, PaymentError> confirmListingFeeCheckout(UUID payerAccountId, UUID sellerId, String orderId,
                                                                           String razorpayPaymentId, String signature) {
        Optional<PaymentEntity> found = paymentRepository.findBySellerId(sellerId)
                .filter(p -> payerAccountId.equals(p.getPayerAccountId()))
                .filter(p -> orderId != null && orderId.equals(p.getRazorpayOrderId()));
        if (found.isEmpty()) {
            return Result.failure(PaymentError.PAYMENT_NOT_FOUND);
        }
        if (!paymentGateway.verifyCheckoutSignature(orderId, razorpayPaymentId, signature)) {
            log.warn("Checkout signature did not verify for seller {}'s listing fee", sellerId);
            return Result.failure(PaymentError.SIGNATURE_INVALID);
        }
        Result<GatewayPayment, PaymentError> confirmed =
                paymentGateway.confirmCapture(razorpayPaymentId, orderId, found.get().getAmount());
        if (confirmed.isFailure()) {
            return Result.failure(confirmed.error());
        }
        return Result.success(transactions.execute(status -> {
            PaymentEntity payment = paymentRepository.findLockedBySellerId(sellerId).orElseThrow();
            return settled(payment)
                    ? toSummary(payment)
                    : markCaptured(payment, confirmed.value().method(), confirmed.value().paymentId());
        }));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentSummary> listingFeeFor(UUID sellerId) {
        return paymentRepository.findBySellerId(sellerId).map(this::toSummary);
    }

    @Override
    public Result<PaymentSummary, PaymentError> payListingFeeFromWallet(UUID payerAccountId, UUID sellerId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return Result.failure(PaymentError.INVALID_AMOUNT);
        }
        return transactions.execute(status -> {
            PaymentEntity payment = paymentRepository.findLockedBySellerId(sellerId)
                    .orElseGet(() -> paymentRepository.save(PaymentEntity.listingFee(payerAccountId, sellerId, amount)));
            if (!payerAccountId.equals(payment.getPayerAccountId())) {
                return Result.<PaymentSummary, PaymentError>failure(PaymentError.PAYMENT_NOT_FOUND);
            }
            if (settled(payment)) {
                return Result.<PaymentSummary, PaymentError>failure(PaymentError.ALREADY_CAPTURED);
            }
            Result<BigDecimal, PaymentError> debit =
                    riderWalletService.debitForListingFee(payerAccountId, payment.getId(), payment.getAmount());
            if (debit.isFailure()) {
                status.setRollbackOnly();
                return Result.<PaymentSummary, PaymentError>failure(debit.error());
            }
            return Result.<PaymentSummary, PaymentError>success(markCaptured(payment, PaymentMethod.SHEOUT_WALLET, null));
        });
    }

    // ------------------------------------------------------------ the partner's UPI QR

    /** How long a QR takes payments. Long enough to open an app and scan; short enough that an old one is not lying around. */
    private static final java.time.Duration QR_LIFETIME = java.time.Duration.ofMinutes(15);

    /**
     * The QR the partner shows at the end of a trip. The same one while it
     * is still good for a few minutes; a new one once it is nearly closed.
     * The Razorpay call is made with no transaction open, as for an order.
     */
    public Result<UpiQr, PaymentError> upiQrForTrip(UUID bookingId) {
        Optional<PaymentEntity> found = ensurePayment(bookingId);
        if (found.isEmpty()) {
            return Result.failure(PaymentError.TRIP_NOT_ENDED);
        }
        PaymentEntity payment = found.get();
        if (settled(payment)) {
            return Result.failure(PaymentError.ALREADY_CAPTURED);
        }
        Instant now = Instant.now();
        if (payment.getRazorpayQrId() != null && payment.getQrExpiresAt() != null
                && payment.getQrExpiresAt().isAfter(now.plus(java.time.Duration.ofMinutes(2)))) {
            return Result.success(new UpiQr(payment.getQrImageUrl(), payment.getAmount(), payment.getQrExpiresAt()));
        }
        String previous = payment.getRazorpayQrId();
        Result<GatewayQr, PaymentError> created = paymentGateway.createUpiQr(payment.getId(), payment.getAmount(),
                "SheOut trip fare", now.plus(QR_LIFETIME));
        if (created.isFailure()) {
            return Result.failure(created.error());
        }
        GatewayQr qr = created.value();
        transactions.executeWithoutResult(status -> paymentRepository.findLockedByBookingId(bookingId).ifPresent(p -> {
            p.setQr(qr.qrId(), qr.imageUrl(), qr.closesAt());
            paymentRepository.save(p);
        }));
        if (previous != null) {
            // Anything already paid on the old one is still found by the
            // webhook - it was closed, not forgotten; see applyQrCredit.
            paymentGateway.closeQr(previous);
        }
        return Result.success(new UpiQr(qr.imageUrl(), payment.getAmount(), qr.closesAt()));
    }

    /**
     * Asks Razorpay whether the QR has been paid - the partner's screen does
     * this while it shows the QR, so a trip is settled even when a webhook
     * is slow or missing. Answers with the payment as it now stands.
     */
    public Result<PaymentSummary, PaymentError> checkUpiQr(UUID bookingId) {
        Optional<PaymentEntity> found = paymentRepository.findByBookingId(bookingId);
        if (found.isEmpty()) {
            return Result.failure(PaymentError.PAYMENT_NOT_FOUND);
        }
        PaymentEntity payment = found.get();
        if (payment.getRazorpayQrId() != null && !settled(payment)) {
            Result<List<GatewayQr.QrPayment>, PaymentError> paid = paymentGateway.qrPayments(payment.getRazorpayQrId());
            if (paid.isSuccess()) {
                for (GatewayQr.QrPayment p : paid.value()) {
                    if (p.captured()) {
                        applyQrCredit(payment.getRazorpayQrId(), p.paymentId(), p.amountPaise(), p.method());
                    }
                }
            }
        }
        return Result.success(toSummary(paymentRepository.findByBookingId(bookingId).orElseThrow()));
    }

    /**
     * A payment arrived on a partner's QR - by webhook (qr_code.credited) or
     * found by checkUpiQr. Captures the fare if it is still owed and the
     * amount is right. If the fare was already paid another way, this second
     * payment is refunded in full: a rider is never charged twice for a trip.
     * False when the QR is not one of ours.
     */
    public boolean applyQrCredit(String qrId, String razorpayPaymentId, long amountPaise, PaymentMethod method) {
        Boolean known = transactions.execute(status -> {
            Optional<PaymentEntity> found = qrId == null ? Optional.empty() : paymentRepository.findLockedByRazorpayQrId(qrId);
            if (found.isEmpty()) {
                return false;
            }
            PaymentEntity payment = found.get();
            if (razorpayPaymentId.equals(payment.getRazorpayPaymentId())) {
                return true;
            }
            long expected = payment.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
            if (!settled(payment) && amountPaise == expected) {
                markCaptured(payment, method == null ? PaymentMethod.UPI : method, razorpayPaymentId);
                return true;
            }
            log.error("QR payment {} on {} for booking {} could not be applied (settled={}, {} paise, expected {}) - refunding",
                    razorpayPaymentId, qrId, payment.getBookingId(), settled(payment), amountPaise, expected);
            return null;
        });
        if (known == null) {
            paymentGateway.refund(razorpayPaymentId);
            return true;
        }
        return known;
    }

    private static void afterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
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
                payment.getCapturedAt(),
                payment.getFareAmount(),
                payment.getPurpose(),
                payment.getSellerId()
        );
    }

    @Override
    public BigDecimal riderWalletBalance(UUID customerAccountId) {
        return riderWalletService.getWallet(customerAccountId).balance();
    }
}
