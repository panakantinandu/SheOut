package com.sheout.driververification;

import com.sheout.auth.AccountRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of an account's verification record.
 * {@code policeVerificationStatus} is null for CUSTOMER accounts - police
 * verification is a driver-only concept.
 */
public record VerificationSummary(
        UUID accountId,
        AccountRole role,
        VerificationStatus genderVerificationStatus,
        VerificationStatus policeVerificationStatus,
        boolean documentSubmitted,
        Instant updatedAt
) {
}
