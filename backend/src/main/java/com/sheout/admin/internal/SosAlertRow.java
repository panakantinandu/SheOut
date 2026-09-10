package com.sheout.admin.internal;

import java.time.Instant;
import java.util.UUID;

/** One active SOS alert with the customer identified. */
public record SosAlertRow(
        UUID id,
        UUID customerAccountId,
        String customerName,
        String customerPhone,
        UUID bookingId,
        double lat,
        double lng,
        int contactsNotified,
        int contactsFailed,
        Instant createdAt
) {
}
