package com.sheout.admin.internal;

import com.sheout.auth.AccountRole;
import com.sheout.driververification.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One account as an operator needs to see it: who they are, what they are
 * on the platform, whether they are verified, and whether they are blocked.
 * <p>
 * Composed here rather than returned by any single module, for the same
 * reason ReviewQueueRow is: the name lives in users, the phone number and
 * the block live in auth, and the verification status lives in
 * driver-verification. None of them should have to know about the other two.
 * <p>
 * verification fields are null for an account with no verification record,
 * and policeVerificationStatus is null for every customer - customers are
 * not police-checked, which the console renders as "not required" rather
 * than as a missing status.
 */
public record AccountOpsRow(
        UUID accountId,
        String name,
        String phoneNumber,
        String email,
        AccountRole role,
        VerificationStatus genderVerificationStatus,
        VerificationStatus policeVerificationStatus,
        boolean blocked,
        Instant blockedAt,
        String blockedByPhone,
        String blockReason,
        Instant createdAt
) {
}
