package com.sheout.notifications;

/**
 * Public rather than internal, matching BookingStatus/PaymentStatus/
 * VerificationStatus - a status other modules read (admin's alert
 * dashboard) belongs on the interface, not behind it.
 * <p>
 * RESOLVED is reached only through SosApi.resolve. An alert is never
 * auto-resolved: a safety alert stays ACTIVE until a human operator
 * explicitly closes it.
 */
public enum SosStatus {
    ACTIVE,
    RESOLVED
}
