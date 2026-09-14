package com.sheout.payments;

public enum PaymentError {
    PAYMENT_NOT_FOUND,
    ALREADY_CAPTURED,
    GATEWAY_ERROR,
    /** Checkout's signature does not match the order and payment it names - the result was not from Razorpay. */
    SIGNATURE_INVALID,
    /** Razorpay says the payment did not go through (failed, or still not captured). */
    NOT_CAPTURED
}
