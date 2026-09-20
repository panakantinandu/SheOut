package com.sheout.users;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code phoneNumber} is composed from auth's {@code AuthApi} at read time,
 * not stored here - see the module README note on why this module never
 * persists a phone number of its own.
 * <p>
 * {@code verified} is a cache updated from driver-verification's
 * {@code AccountVerified} event (see {@code UserProfileEventListeners}) -
 * convenient for display, but nothing safety-critical should gate on it;
 * see {@code DriverProfileService.setOnlineStatus} for the one place that
 * matters, which re-checks live instead.
 */
public record CustomerProfileSummary(
        UUID accountId,
        String name,
        String phoneNumber,
        /** Home and Work as places, not sentences - see SavedPlace. Null when unset. */
        SavedPlace home,
        SavedPlace work,
        /** Null for an account that has not completed its profile since dates of birth were required. */
        LocalDate dateOfBirth,
        /** Optional; null when none is set. */
        String email,
        /**
         * Resolved at read time, never stored, and null when no browser could
         * load it (local-disk storage) - clients render a silhouette. Use
         * hasProfilePhoto to ask whether she still needs to add one.
         */
        String profilePhotoUrl,
        boolean hasProfilePhoto,
        /** False until she has been shown the introduction - see the apps' Onboarding screen. */
        boolean onboardingSeen,
        /** Name, date of birth and photo are all on file - what the apps' completion screen checks. */
        boolean profileComplete,
        boolean verified,
        TrustStats trustStats,
        Instant updatedAt
) {
}
