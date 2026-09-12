package com.sheout.admin.internal;

import com.sheout.auth.AccountRole;
import com.sheout.driververification.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One driver awaiting review, joined across driver-verification (statuses),
 * users (name) and auth (phone). name/phoneNumber are null when the account
 * has no profile row yet - a real case, since verification starts at signup
 * before any profile is filled in.
 */
public record ReviewQueueRow(
        UUID accountId,
        String name,
        String phoneNumber,
        AccountRole role,
        VerificationStatus genderVerificationStatus,
        VerificationStatus policeVerificationStatus,
        boolean documentSubmitted,
        Instant updatedAt,
        /** Shown as a badge in the queue: reviewing a blocked account is almost always a mistake. */
        boolean blocked
) {
}
