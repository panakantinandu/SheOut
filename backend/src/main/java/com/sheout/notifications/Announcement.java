package com.sheout.notifications;

import java.time.Instant;
import java.util.UUID;

/**
 * One broadcast that was sent, with what actually went out.
 * <p>
 * pushAccepted counts audiences FCM took the message for (one or two), not
 * phones: a topic broadcast is a single call that FCM fans out, and it never
 * reports how many devices received it. A device count here would be a number
 * nobody measured.
 */
public record Announcement(
        UUID id,
        String title,
        String body,
        AnnouncementAudience audience,
        Instant sentAt,
        int pushAccepted,
        int pushFailed,
        int emailsSent,
        int emailsFailed,
        int smsSent,
        int smsFailed,
        boolean smsRequested,
        /** What an operator needs to know afterwards: a channel that was off, a provider that refused. */
        String note
) {
}
