package com.sheout.notifications.internal;

public enum NotificationError {
    /** The account has no address this channel can reach (e.g. a Google-signup account with no phone number, for SMS). */
    NO_RECIPIENT_ADDRESS,
    /** The channel's provider rejected or failed the send - see the log row's failureReason for detail. */
    PROVIDER_ERROR
}
