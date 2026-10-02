package com.sheout.admin.internal;

import com.sheout.notifications.SosDeliveryChannel;
import com.sheout.notifications.SosTriggerSource;
import java.time.Instant;
import java.util.UUID;

/** One active SOS alert with the customer identified. */
public record SosAlertRow(
        UUID id,
        UUID customerAccountId,
        String customerName,
        /** Her profile photo, for the row's avatar - null shows her initials. */
        String photoUrl,
        /** RIDER or PARTNER - whoever pressed it. */
        String raisedBy,
        String customerPhone,
        UUID bookingId,
        double lat,
        double lng,
        int contactsNotified,
        int contactsFailed,
        Instant createdAt,
        /** An alert from a blocked account still needs answering - the badge is context, not a filter. */
        boolean customerBlocked,
        /** The delivery evidence beside the contacts reached - see SosTriggerSource / SosDeliveryChannel. */
        SosTriggerSource triggerSource,
        SosDeliveryChannel deliveryChannel,
        boolean smsFallbackOpened,
        Instant triggeredAt
) {
}
