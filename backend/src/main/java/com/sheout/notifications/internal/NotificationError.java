package com.sheout.notifications.internal;

public enum NotificationError {
    NO_RECIPIENT_ADDRESS,
    PROVIDER_ERROR,
    /** The channel has no credentials on this deployment, so nothing was attempted. */
    NOT_CONFIGURED,
    /**
     * The address will never work again - FCM says the device token is
     * unregistered or invalid. The dispatcher deletes the token, so every
     * later notification does not fail on it too.
     */
    RECIPIENT_GONE
}
