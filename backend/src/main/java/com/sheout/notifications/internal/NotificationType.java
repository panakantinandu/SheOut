package com.sheout.notifications.internal;

/**
 * What a notification is about. Drives the copy (NotificationEventListeners),
 * how it is delivered (DeliveryPolicy) and where tapping it goes.
 * <p>
 * Stored by name in notification_log, so a value is never renamed or removed
 * while rows carry it.
 */
public enum NotificationType {
    BOOKING_REQUESTED,
    BOOKING_ACCEPTED,
    /** The partner is close to the pickup - see dispatch's DriverArriving. */
    DRIVER_ARRIVING,
    BOOKING_COMPLETED,
    BOOKING_CANCELLED,
    /** Dispatch searched and nobody took the trip. */
    NO_DRIVERS_AVAILABLE,
    ACCOUNT_VERIFIED,

    /** Her document was looked at and turned down, with the reason. */
    ACCOUNT_VERIFICATION_REJECTED,

    /** To operations: somebody is waiting on a review. */
    VERIFICATION_SUBMITTED,
    /** The rider's own record of an SOS she raised and who it reached. */
    SOS_ALERT,
    /** An SOS raised by a rider, sent to operators. */
    SOS_OPERATOR_ALERT,
    /** An operator replied to a support ticket - see support's SupportReplyPosted. */
    SUPPORT_REPLY,
    /** A trip was offered to this partner - see dispatch's DriverOffered. */
    DRIVER_OFFER,
    PAYOUT_PAID,
    PAYMENT_RECEIPT
}
