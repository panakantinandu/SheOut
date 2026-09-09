package com.sheout.payments;

/** Public (like BookingError) - PaymentApi is a real cross-module/cross-app interface, not just an internal detail. */
public enum PaymentError {
    PAYMENT_NOT_FOUND,
    ALREADY_CAPTURED,
    GATEWAY_ERROR
}
