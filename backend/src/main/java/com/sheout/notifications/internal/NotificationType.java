package com.sheout.notifications.internal;

/**
 * What triggered a notification - drives both the log entry and (in
 * NotificationEventListeners) which message copy gets used. Scoped to
 * exactly the events named in the notifications spec (BookingRequested,
 * BookingAccepted, BookingCompleted, BookingCancelled, AccountVerified) plus
 * SOS_ALERT for Part B - other real events (BookingMatched, BookingStarted,
 * AccountRegistered) exist and could be wired the same way later, but
 * weren't in the named list, so they're left out of this pass rather than
 * guessed at.
 */
public enum NotificationType {
    BOOKING_REQUESTED,
    BOOKING_ACCEPTED,
    BOOKING_COMPLETED,
    BOOKING_CANCELLED,
    ACCOUNT_VERIFIED,
    SOS_ALERT
}
