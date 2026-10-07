package com.sheout.driververification;

import com.sheout.auth.AccountRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every check on one account, and where they leave her.
 * <p>
 * {@code overall} is worked out, never stored, so it cannot disagree with
 * the checks it is made of: READY_TO_APPROVE only when every required check
 * is PASSED or APPROVED_BY_PERSON.
 */
public record VerificationChecks(UUID accountId, AccountRole role, List<VerificationCheck> checks, Overall overall) {

    public enum Overall {
        /** Every required check is passed or accepted by a person. */
        READY_TO_APPROVE,
        /** A required check failed automatically; a person sends it back, or overrides where allowed. */
        FAILED_CHECK,
        /** A required check is waiting for a person. */
        NEEDS_REVIEW,
        /** Nothing for a person yet: she still has something to send. */
        WAITING_FOR_APPLICANT
    }

    public Optional<VerificationCheck> find(CheckType type) {
        return checks.stream().filter(c -> c.type() == type).findFirst();
    }

    /** Worked out from the required checks, worst first. */
    public static Overall overallOf(List<VerificationCheck> checks) {
        List<VerificationCheck> required = checks.stream().filter(VerificationCheck::required).toList();
        if (required.stream().anyMatch(c -> c.status() == CheckStatus.FAILED_AUTO)) {
            return Overall.FAILED_CHECK;
        }
        if (required.stream().anyMatch(c -> c.status() == CheckStatus.NEEDS_REVIEW)) {
            return Overall.NEEDS_REVIEW;
        }
        if (required.stream().allMatch(c -> c.status().satisfied())) {
            return Overall.READY_TO_APPROVE;
        }
        return Overall.WAITING_FOR_APPLICANT;
    }
}
