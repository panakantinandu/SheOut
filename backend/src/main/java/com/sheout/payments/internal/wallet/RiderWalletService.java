package com.sheout.payments.internal.wallet;

import com.sheout.payments.PaymentError;
import com.sheout.payments.PaymentMethod;
import com.sheout.payments.PaymentStatus;
import com.sheout.payments.internal.gateway.GatewayOrder;
import com.sheout.payments.internal.gateway.GatewayPayment;
import com.sheout.payments.internal.gateway.PaymentGateway;
import com.sheout.sharedkernel.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A rider's SheOut wallet: a closed-loop balance she tops up through Razorpay
 * and spends only on her own trips.
 * <p>
 * DELIBERATELY NO TRANSFERS AND NO WITHDRAWAL. A balance that can be sent to
 * another person or cashed out is an open or semi-closed prepaid instrument,
 * which in India needs an RBI licence and full KYC. A balance that can only
 * buy SheOut trips is a closed system PPI, which does not. "Send money" was
 * removed from the app for this reason - it is not a missing feature.
 * <p>
 * Money only ever enters on Razorpay's word. The app reporting a successful
 * Checkout is not enough: the signature has to verify under our secret, and
 * Razorpay itself has to say the payment is captured for exactly this order
 * and amount. Top-ups credit once however many times verify or the webhook
 * arrive - the top-up row is locked and its status checked, and the ledger
 * has a unique index behind that (V23).
 * <p>
 * Gateway calls run with no transaction open, as everywhere else in payments.
 */
@Service
public class RiderWalletService {

    private static final Logger log = LoggerFactory.getLogger(RiderWalletService.class);

    /** Smallest top-up. Below this the gateway fee is a silly share of the money. */
    public static final BigDecimal MIN_TOPUP = new BigDecimal("10.00");
    /** Largest single top-up. */
    public static final BigDecimal MAX_TOPUP = new BigDecimal("10000.00");
    /**
     * Most a wallet may hold. Enough for a month of commuting; small enough
     * that a compromised account is not a large prize.
     */
    public static final BigDecimal MAX_BALANCE = new BigDecimal("10000.00");

    private final RiderWalletRepository wallets;
    private final RiderWalletEntryRepository entries;
    private final WalletTopupRepository topups;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactions;

    RiderWalletService(RiderWalletRepository wallets, RiderWalletEntryRepository entries, WalletTopupRepository topups,
                       PaymentGateway paymentGateway, PlatformTransactionManager transactionManager) {
        this.wallets = wallets;
        this.entries = entries;
        this.topups = topups;
        this.paymentGateway = paymentGateway;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public WalletView getWallet(UUID customerAccountId) {
        BigDecimal balance = wallets.findByCustomerAccountId(customerAccountId)
                .map(RiderWalletEntity::getBalance)
                .orElse(BigDecimal.ZERO);
        return new WalletView(balance, MIN_TOPUP, MAX_TOPUP, MAX_BALANCE);
    }

    public Page<WalletEntryView> statement(UUID customerAccountId, Pageable pageable) {
        return entries.findByCustomerAccountIdOrderByCreatedAtDesc(customerAccountId, pageable)
                .map(e -> new WalletEntryView(e.getId(), e.getType(), e.getAmount(), e.getBalanceAfter(),
                        e.getBookingId(), e.getCreatedAt()));
    }

    /**
     * Starts a top-up: records what she asked to add and opens a Razorpay
     * order for it. Nothing is credited here.
     */
    public Result<TopupCheckout, PaymentError> startTopup(UUID customerAccountId, BigDecimal requested) {
        if (requested == null || requested.scale() > 2) {
            return Result.failure(PaymentError.INVALID_AMOUNT);
        }
        BigDecimal amount = requested.setScale(2, RoundingMode.UNNECESSARY);
        if (amount.compareTo(MIN_TOPUP) < 0 || amount.compareTo(MAX_TOPUP) > 0) {
            return Result.failure(PaymentError.INVALID_AMOUNT);
        }
        // Checked against the balance now. Two top-ups started together can
        // both pass, and both will be credited if both are paid - money she
        // has actually handed over is never refused at the door.
        if (getWallet(customerAccountId).balance().add(amount).compareTo(MAX_BALANCE) > 0) {
            return Result.failure(PaymentError.INVALID_AMOUNT);
        }

        WalletTopupEntity topup = transactions.execute(status -> topups.save(new WalletTopupEntity(customerAccountId, amount)));
        Result<GatewayOrder, PaymentError> order = paymentGateway.createOrder(topup.getId(), amount);
        if (order.isFailure()) {
            transactions.executeWithoutResult(status -> topups.findLockedById(topup.getId()).ifPresent(t -> {
                t.markFailed(null, "Razorpay order creation failed");
                topups.save(t);
            }));
            return Result.failure(order.error());
        }
        String orderId = order.value().orderId();
        transactions.executeWithoutResult(status -> topups.findLockedById(topup.getId()).ifPresent(t -> {
            t.attachOrder(orderId);
            topups.save(t);
        }));
        long paise = amount.movePointRight(2).longValueExact();
        return Result.success(new TopupCheckout(topup.getId(), paymentGateway.publicKeyId(), orderId, paise, "INR"));
    }

    /** Checkout said the top-up succeeded. Verified with Razorpay before a rupee is credited. */
    public Result<WalletView, PaymentError> confirmTopup(UUID customerAccountId, UUID topupId, String orderId,
                                                         String razorpayPaymentId, String signature) {
        Optional<WalletTopupEntity> found = topups.findById(topupId)
                .filter(t -> t.getCustomerAccountId().equals(customerAccountId))
                .filter(t -> orderId.equals(t.getRazorpayOrderId()));
        if (found.isEmpty()) {
            return Result.failure(PaymentError.TOPUP_NOT_FOUND);
        }
        if (!paymentGateway.verifyCheckoutSignature(orderId, razorpayPaymentId, signature)) {
            log.warn("Top-up {} checkout signature did not verify", topupId);
            return Result.failure(PaymentError.SIGNATURE_INVALID);
        }
        Result<GatewayPayment, PaymentError> confirmed =
                paymentGateway.confirmCapture(razorpayPaymentId, orderId, found.get().getAmount());
        if (confirmed.isFailure()) {
            return Result.failure(confirmed.error());
        }
        transactions.executeWithoutResult(status -> {
            WalletTopupEntity topup = topups.findLockedById(topupId).orElseThrow();
            if (topup.getStatus() != PaymentStatus.CAPTURED) {
                credit(topup, confirmed.value().paymentId(), confirmed.value().method());
            }
        });
        return Result.success(getWallet(customerAccountId));
    }

    /**
     * Razorpay's webhook, for an order that belongs to a top-up. Returns
     * false when the order is not a top-up, so the caller can look elsewhere.
     * The caller has already verified the webhook signature.
     */
    @Transactional
    public boolean applyWebhook(String orderId, String razorpayPaymentId, boolean captured, String failureReason,
                                PaymentMethod method) {
        Optional<WalletTopupEntity> byOrder = orderId == null ? Optional.empty() : topups.findByRazorpayOrderId(orderId);
        if (byOrder.isEmpty()) {
            return false;
        }
        WalletTopupEntity topup = topups.findLockedById(byOrder.get().getId()).orElseThrow();
        if (topup.getStatus() == PaymentStatus.CAPTURED) {
            return true;
        }
        if (captured) {
            credit(topup, razorpayPaymentId, method);
        } else {
            topup.markFailed(razorpayPaymentId, failureReason);
            topups.save(topup);
        }
        return true;
    }

    /**
     * Takes a trip's fare from her balance. Runs only inside the caller's
     * transaction, which also holds the payment row's lock and captures the
     * payment - the debit and the capture commit together or not at all.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Result<BigDecimal, PaymentError> debitForTrip(UUID customerAccountId, UUID bookingId, BigDecimal fare) {
        if (entries.existsByBookingIdAndType(bookingId, RiderWalletEntryEntity.Type.TRIP_PAYMENT)) {
            return Result.failure(PaymentError.ALREADY_CAPTURED);
        }
        RiderWalletEntity wallet = lockedWallet(customerAccountId);
        if (wallet.getBalance().compareTo(fare) < 0) {
            return Result.failure(PaymentError.INSUFFICIENT_BALANCE);
        }
        wallet.debit(fare);
        wallets.save(wallet);
        entries.save(RiderWalletEntryEntity.tripPayment(customerAccountId, fare, wallet.getBalance(), bookingId));
        log.info("Trip {} paid from wallet of {} - {}", bookingId, customerAccountId, fare);
        return Result.success(wallet.getBalance());
    }

    private void credit(WalletTopupEntity topup, String razorpayPaymentId, PaymentMethod method) {
        topup.markCaptured(razorpayPaymentId, method);
        topups.save(topup);
        RiderWalletEntity wallet = lockedWallet(topup.getCustomerAccountId());
        wallet.credit(topup.getAmount());
        wallets.save(wallet);
        entries.save(RiderWalletEntryEntity.topup(topup.getCustomerAccountId(), topup.getAmount(), wallet.getBalance(),
                topup.getId()));
        log.info("Top-up {} credited - {}", topup.getId(), topup.getAmount());
    }

    private RiderWalletEntity lockedWallet(UUID customerAccountId) {
        wallets.createIfAbsent(customerAccountId);
        return wallets.findLockedByCustomerAccountId(customerAccountId).orElseThrow();
    }

    public record WalletView(BigDecimal balance, BigDecimal minTopup, BigDecimal maxTopup, BigDecimal maxBalance) {
    }

    public record WalletEntryView(UUID id, RiderWalletEntryEntity.Type type, BigDecimal amount, BigDecimal balanceAfter,
                                  UUID bookingId, Instant createdAt) {
    }

    /** What Checkout.js is opened with for a top-up. keyId is Razorpay's publishable key, not a secret. */
    public record TopupCheckout(UUID topupId, String keyId, String orderId, long amountPaise, String currency) {
    }
}
