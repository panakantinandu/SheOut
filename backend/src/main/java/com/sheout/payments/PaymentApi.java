package com.sheout.payments;

import com.sheout.sharedkernel.Result;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Deliberately minimal: what another module needs to know about a trip's
 * payment, and the one other thing SheOut is paid for - a seller's listing
 * fee. Trip payment creation is not here - it is driven by BookingCompleted
 * (see BookingCompletedListener, internal), so booking never needs to know
 * payments exists.
 * <p>
 * There is no cash method any more. Every fare is paid through SheOut - from
 * the rider's wallet or online - so it reaches the partner's wallet with a
 * record behind it, and nobody can mark a fare paid by tapping a button.
 */
public interface PaymentApi {

    Result<PaymentSummary, PaymentError> getPaymentStatus(UUID bookingId);

    /**
     * What a partner receives from this fare at today's commission - the same
     * PlatformCommission arithmetic her payout is worked out with, so the
     * earnings an offer shows are the earnings she is paid.
     */
    java.math.BigDecimal partnerShareOf(java.math.BigDecimal fare);

    /** Today's platform commission as a percentage, e.g. 18.00. */
    java.math.BigDecimal commissionPercent();

    /** A rider's SheOut wallet balance; zero when she has never topped up. For the console. */
    java.math.BigDecimal riderWalletBalance(UUID customerAccountId);

    /**
     * A refund or goodwill credit from the console, into the rider's SheOut
     * wallet. With a trip: only for a captured payment, and never more in all
     * than she paid for it. Idempotent by refundId. Who may issue how much is
     * the console's rule (staff.RefundLimits and its approvals), not this one's.
     */
    Result<java.math.BigDecimal, PaymentError> refundToWallet(UUID customerAccountId, UUID refundId, UUID bookingId,
                                                              java.math.BigDecimal amount);

    // ------------------------------------------------------------ listing fees

    /**
     * Opens (or reopens) the Razorpay order for a seller's fee for one month
     * ({@code period}: 1 for her first paid month). One payment row per
     * seller and month: asking again before paying reuses it, so a closed
     * Checkout or a failed card never leaves two fees to reconcile. The
     * amount is the marketplace's to decide and is fixed on the row the
     * first time; an already-captured month is refused as ALREADY_CAPTURED.
     */
    Result<ListingFeeCheckout, PaymentError> startListingFeeCheckout(UUID payerAccountId, UUID sellerId, int period,
                                                                    BigDecimal amount);

    /**
     * Checkout reported success. Verified with Razorpay exactly as a trip is -
     * the signature, then the capture itself for this order and amount -
     * before ListingFeePaid is published. A no-op if the webhook got there
     * first.
     */
    Result<PaymentSummary, PaymentError> confirmListingFeeCheckout(UUID payerAccountId, UUID sellerId, int period,
                                                                    String orderId, String razorpayPaymentId, String signature);

    /** The seller's fee for one month as it stands, if one was ever started. */
    Optional<PaymentSummary> listingFeeFor(UUID sellerId, int period);

    /** Every month a seller has paid for, oldest first - her payment history. */
    java.util.List<SellerFeePayment> paidListingFees(UUID sellerId);

    /**
     * The fee paid from her SheOut wallet: the debit and the capture in one
     * transaction, and ListingFeePaid published with it. INSUFFICIENT_BALANCE
     * when her balance is short; ALREADY_CAPTURED when it is paid already.
     */
    Result<PaymentSummary, PaymentError> payListingFeeFromWallet(UUID payerAccountId, UUID sellerId, int period,
                                                                 BigDecimal amount);
}
