package com.sheout.notifications.internal;

/**
 * Only SMS has a real implementation this pass (see channel/TwilioSmsChannel)
 * - PUSH is kept as a value here (and on the log table) so a future
 * FcmPushChannel slots in without a schema change, but nothing constructs
 * one today. See channel/NotificationChannel's Javadoc for why.
 */
public enum NotificationChannelType {
    SMS,
    PUSH
}
