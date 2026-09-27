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

    // ------------------------------------------------------------ listing fees

    /**
     * Opens (or reopens) the Razorpay order for a seller's listing fee. One
     * payment row per seller: asking again before paying reuses it, so a
     * closed Checkout or a failed card never leaves two fees to reconcile.
     * The amount is the marketplace's to decide and is fixed on the row the
     * first time; an already-captured fee is refused as ALREADY_CAPTURED.
     */
    Result<ListingFeeCheckout, PaymentError> startListingFeeCheckout(UUID payerAccountId, UUID sellerId, BigDecimal amount);

    /**
     * Checkout reported success. Verified with Razorpay exactly as a trip is -
     * the signature, then the capture itself for this order and amount -
     * before ListingFeePaid is published. A no-op if the webhook got there
     * first.
     */
    Result<PaymentSummary, PaymentError> confirmListingFeeCheckout(UUID payerAccountId, UUID sellerId, String orderId,
                                                                    String razorpayPaymentId, String signature);

    /** The seller's listing fee as it stands, if one was ever started. */
    Optional<PaymentSummary> listingFeeFor(UUID sellerId);
}
