package com.sheout.driververification.internal.checks;

import com.sheout.driververification.CheckStatus;
import com.sheout.driververification.CheckType;

import java.util.UUID;

/**
 * The swap point for whoever answers one kind of check, the same shape as
 * OtpSender and VerificationProvider: the workflow depends on this, and who
 * answers is configuration (VERIFICATION_CHECK_PROVIDERS, see CheckProviders).
 * <p>
 * The only implementation is ManualCheckProvider, which answers "a person
 * looks" for everything. No vendor is integrated, and no vendor's API is
 * guessed at here: a vendor's implementation gathers what it needs through
 * other modules' public APIs at the moment it is asked.
 * <p>
 * A provider can pass a check, set it aside for a person, or report a
 * certain failure. It cannot reject anybody and cannot approve on a
 * person's behalf: APPROVED_BY_PERSON from a provider is refused, and a
 * failure still goes to a person, who sends it back or overrides it.
 * <p>
 * It is asked once per subject (a document, or an ID submission), not on
 * every read, so a paid vendor is not charged for the console being opened.
 */
public interface CheckProvider {

    /** Shown on the check and in the audit trail. */
    String name();

    boolean handles(CheckType type);

    Verdict check(Request request);

    /**
     * subjectId is the document the check is about (null for the ID and
     * selfie, which live on her verification record); subjectVersion
     * changes whenever she sends it again.
     */
    record Request(UUID accountId, CheckType type, UUID subjectId, String subjectVersion) {
    }

    /** status is PASSED, NEEDS_REVIEW or FAILED_AUTO. reference is the vendor's own id for the answer, if any. */
    record Verdict(CheckStatus status, String reason, String reference) {

        public Verdict {
            if (status != CheckStatus.PASSED && status != CheckStatus.NEEDS_REVIEW && status != CheckStatus.FAILED_AUTO) {
                throw new IllegalArgumentException("A provider answers PASSED, NEEDS_REVIEW or FAILED_AUTO, not " + status);
            }
        }
    }
}
