package com.sheout.notifications;

/**
 * Public because SosApi returns it, same as BookingError/PaymentError.
 */
public enum SosError {
    ALERT_NOT_FOUND,
    ALREADY_RESOLVED
}
