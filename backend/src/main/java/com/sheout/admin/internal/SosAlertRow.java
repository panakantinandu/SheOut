package com.sheout.admin.internal;

import com.sheout.notifications.SosDeliveryChannel;
import com.sheout.notifications.SosTriggerSource;
import java.time.Instant;
import java.util.List;
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
        Instant triggeredAt,
        /**
         * The other person on the trip it was raised on - the partner when a
         * rider pressed it, the rider when a partner did. Null with no trip,
         * or before a partner was assigned. The operator calls them next if
         * she cannot be reached (docs/sos-runbook.md).
         */
        UUID otherPartyAccountId,
        String otherPartyName,
        String otherPartyPhone,
        /**
         * Her saved emergency contacts, in their saved order (the reveal's index).
         * SheOut cannot text them yet (no SMS provider), so the operator calls them.
         */
        List<Contact> emergencyContacts
) {
    /** The phone is masked in the console like any other; Show reveals it. */
    public record Contact(String name, String relationship, String phoneNumber) {
    }
}
