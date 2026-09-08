package com.sheout.users;

import java.util.UUID;

/**
 * Owned by a customer's profile. Exposed via {@link EmergencyContactsApi}
 * specifically so the notifications module (not built yet) can read these
 * for SOS alerts without ever querying this module's tables directly.
 */
public record EmergencyContact(
        UUID id,
        UUID customerAccountId,
        String name,
        String phoneNumber,
        String relationship
) {
}
