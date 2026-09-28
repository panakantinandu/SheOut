package com.sheout.notifications;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of an SOS alert for other modules (today: admin's alert
 * dashboard).
 * <p>
 * Deliberately carries only this module's own data - customerAccountId
 * rather than a customer name or phone number. Resolving that id to a
 * person is the caller's job via users/auth's own interfaces; duplicating
 * it here would make notifications depend on profile data it has no reason
 * to own. resolvedAt/resolvedBy are null while the alert is ACTIVE.
 */
public record SosAlertSummary(
        UUID id,
        UUID customerAccountId,
        UUID bookingId,
        double lat,
        double lng,
        SosStatus status,
        int contactsNotified,
        int contactsFailed,
        Instant createdAt,
        Instant resolvedAt,
        UUID resolvedBy,
        /** What raised it - the button, a discreet gesture, or the phone's own shortcut. */
        SosTriggerSource triggerSource,
        /** Live over data, or held on her phone without signal and sent when it came back. */
        SosDeliveryChannel deliveryChannel,
        /** Her phone opened its own SMS app to her contacts - the fallback when data was slow or absent. */
        boolean smsFallbackOpened,
        /** When she raised it on the phone; earlier than createdAt when it waited in the queue. */
        Instant triggeredAt
) {
}
