package com.sheout.payments;

/**
 * How a payment was actually settled.
 * <p>
 * CASH is historical only: it was handed to the partner and confirmed by
 * her, and is no longer accepted - every fare now goes through SheOut.
 * SHEOUT_WALLET is her own SheOut balance. The rest come back from Razorpay
 * Checkout, which offers every method itself; the value recorded is the one the rider chose
 * there, read from Razorpay at capture. ONLINE covers a method Razorpay
 * reports that is not listed here.
 * <p>
 * A payment row is created PENDING with UPI as a placeholder before anyone
 * has paid; the method is overwritten when it is captured.
 */
public enum PaymentMethod {
    UPI,
    CASH,
    CARD,
    NETBANKING,
    /** A third-party wallet chosen inside Razorpay Checkout (Paytm, PhonePe wallet...). */
    WALLET,
    ONLINE,

    /** Paid from her own SheOut wallet balance. */
    SHEOUT_WALLET,

    /** A promotion paid the whole fare - see PaymentService.settleCoveredByPromotion. */
    PROMO_CREDIT
}
