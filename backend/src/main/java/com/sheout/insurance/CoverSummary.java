package com.sheout.insurance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A policy as the apps describe it - the passenger cover in the Safety
 * Center, a partner's own group cover on her profile. No premium, and no
 * internal id.
 */
public record CoverSummary(
        PolicyKind kind,
        String insurerName,
        String policyNumber,
        BigDecimal sumInsured,
        String coverageSummary,
        String claimSteps,
        String claimsPhone,
        String claimsUrl,
        String policySummaryUrl,
        LocalDate effectiveTo,
        /** Her membership number in a group cover; null for the passenger cover. */
        String memberId
) {
}
