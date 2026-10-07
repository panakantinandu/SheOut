package com.sheout.driververification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One check, as a reviewer or the applicant's app sees it.
 * <p>
 * {@code decidedBy} is null when the software set the status;
 * {@code decidedByRole} says who did (SYSTEM, OPERATOR or PROVIDER).
 * {@code subjectId} is the document the check is about, when it is about
 * one. The override fields are set only when a failed check was accepted by
 * two people.
 */
public record VerificationCheck(
        CheckType type,
        boolean required,
        CheckStatus status,
        String reason,
        List<Finding> findings,
        String provider,
        UUID subjectId,
        UUID decidedBy,
        String decidedByRole,
        Instant decidedAt,
        String overrideNote,
        UUID overrideRequestedBy,
        UUID overrideApprovedBy
) {

    /** A finding, with what it is about when that helps ("on 2 other accounts"). */
    public record Finding(CheckFinding code, String detail) {
    }

    /** Whether a person may accept this check over its failure, with a note and a second sign-off. */
    @com.fasterxml.jackson.annotation.JsonProperty("overridable")
    public boolean overridable() {
        return status == CheckStatus.FAILED_AUTO
                && findings.stream().anyMatch(f -> f.code().fails())
                && findings.stream().filter(f -> f.code().fails()).allMatch(f -> f.code().overridable());
    }
}
