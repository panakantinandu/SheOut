package com.sheout.driververification;

import java.time.Instant;

/**
 * Where a partner stands on the verification consent: the version in force,
 * the version she last agreed to and when, and whether that is enough. The
 * app shows the consent screen whenever {@code current} is false.
 */
public record VerificationConsentStatus(
        String currentVersion,
        String acceptedVersion,
        Instant acceptedAt,
        boolean current
) {
}
