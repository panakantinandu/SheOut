package com.sheout.insurance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The cover one trip had, for the "Insured trip" chip and its sheet: who
 * insures it, under which policy, for how much, what is covered and how to
 * claim. Exists only for a trip that started while a policy was active.
 * <p>
 * Deliberately no premium here: what SheOut pays for the cover is its own
 * cost and nothing the rider or partner is charged.
 */
public record TripCover(
        UUID bookingId,
        PolicyKind kind,
        String insurerName,
        String policyNumber,
        BigDecimal sumInsured,
        String coverageSummary,
        String claimSteps,
        String claimsPhone,
        String claimsUrl,
        String policySummaryUrl,
        Instant coverageStartedAt,
        Instant coverageEndedAt
) {
}
