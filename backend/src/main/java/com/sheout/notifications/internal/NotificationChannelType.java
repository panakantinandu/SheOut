package com.sheout.notifications.internal;

/** How one copy of a notification was delivered. Stored by name on notification_deliveries. */
public enum NotificationChannelType {
    SMS,
    PUSH,
    EMAIL
}
