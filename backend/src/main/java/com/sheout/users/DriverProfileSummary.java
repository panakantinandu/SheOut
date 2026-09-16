package com.sheout.users;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code phoneNumber} is composed from auth's {@code AuthApi} at read time,
 * not stored here. {@code verified} is a cache from the {@code AccountVerified}
 * event - display-only; the actual online-status gate re-checks live via
 * driver-verification's {@code VerificationApi}. See {@link OnlineStatus}.
 */
public record DriverProfileSummary(
        UUID accountId,
        String name,
        String phoneNumber,
        VehicleType vehicleType,
        String vehicleRegistrationNumber,
        /**
         * Her PAN, for payout tax compliance, or null if she has not given
         * one. Optional, and never an identity check.
         *
         * Returned to the partner herself and to an operator, and to nobody
         * else - the rider-facing driver card is built field by field in
         * dispatch and does not include it. Anything new that hands a
         * DriverProfileSummary to a customer has to leave this out.
         */
        String panNumber,
        OnlineStatus onlineStatus,
        boolean verified,
        /**
         * Resolved at read time from the stored key, never persisted - a
         * presigned URL is stale within minutes. Null when this partner has
         * no photo yet, which clients render as a silhouette rather than a
         * broken image.
         */
        String profilePhotoUrl,
        /**
         * Whether a photo exists at all, which is NOT the same question as
         * whether profilePhotoUrl is set.
         *
         * With local-disk storage the stored file cannot be served to a
         * browser, so the URL is null even though she has uploaded one.
         * Without this flag every screen that asks "does she still need to
         * add a photo" would answer yes forever and keep nagging somebody
         * who has already done it.
         */
        boolean hasProfilePhoto,
        /** Null for an account that has not completed its profile since dates of birth were required. */
        LocalDate dateOfBirth,
        /** Optional; null when none is set. */
        String email,
        /** Name, date of birth and photo are all on file - what the apps' completion screen checks. */
        boolean profileComplete,
        TrustStats trustStats,
        Instant updatedAt
) {
}
