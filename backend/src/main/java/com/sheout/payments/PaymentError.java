package com.sheout.payments;

public enum PaymentError {
    PAYMENT_NOT_FOUND,
    ALREADY_CAPTURED,
    GATEWAY_ERROR,
    /** Checkout's signature does not match the order and payment it names - the result was not from Razorpay. */
    SIGNATURE_INVALID,
    /** Razorpay says the payment did not go through (failed, or still not captured). */
    NOT_CAPTURED,

    /**
     * Cash is no longer a way to pay. Every fare goes through SheOut so it
     * lands in the partner's wallet with a record behind it - see
     * PaymentService.
     */
    CASH_NOT_ACCEPTED,

    /** The trip has not ended yet, so there is nothing to pay. */
    TRIP_NOT_ENDED,

    /** Her SheOut wallet holds less than the fare. */
    INSUFFICIENT_BALANCE,

    /** A top-up below the minimum, above the maximum, or that would take the balance past its cap. */
    INVALID_AMOUNT,

    /** No top-up with that id belongs to this rider. */
    TOPUP_NOT_FOUND
}
