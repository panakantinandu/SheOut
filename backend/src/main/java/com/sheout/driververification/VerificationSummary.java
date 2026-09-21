package com.sheout.driververification;

import com.sheout.auth.AccountRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of an account's verification record.
 * {@code policeVerificationStatus} is null for CUSTOMER accounts - police
 * verification is a driver-only concept.
 * <p>
 * {@code rejectionReason} is what the operator wrote, and null unless the
 * last decision was a rejection. It is on the summary because the person it
 * is about is the one who most needs it: "we could not accept this" without
 * a reason leaves her guessing which part to redo.
 */
public record VerificationSummary(
        UUID accountId,
        AccountRole role,
        VerificationStatus genderVerificationStatus,
        VerificationStatus policeVerificationStatus,
        boolean documentSubmitted,
        String rejectionReason,
        Instant updatedAt
) {
}
