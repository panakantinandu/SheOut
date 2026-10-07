package com.sheout.driververification;

/**
 * Where one check stands.
 * <p>
 * There is no "rejected" here on purpose. Software may pass a clean case
 * (PASSED) or set one aside (NEEDS_REVIEW, FAILED_AUTO), and only a person
 * turns somebody down. When a person does, the check goes back to PENDING,
 * waiting for her to send it again, and the decision is on the audit trail.
 * <p>
 * FAILED_AUTO is a fact the software is sure of, such as a licence whose
 * expiry date has passed. It still rejects nobody: a person either sends it
 * back with a reason, or, where the finding allows it, overrides it with a
 * written note and a second person's sign-off.
 * <p>
 * Stored by name; never rename a value while rows carry it.
 */
public enum CheckStatus {
    /** Nothing to check yet: she has not sent it, or was asked to send it again. */
    PENDING,
    /** The software checked it and found nothing to look at. */
    PASSED,
    /** A person has to look. */
    NEEDS_REVIEW,
    /** The software found something certain. A person sends it back, or overrides where allowed. */
    FAILED_AUTO,
    /** A person looked and accepted it. */
    APPROVED_BY_PERSON;

    /** Counts towards "ready to approve". */
    public boolean satisfied() {
        return this == PASSED || this == APPROVED_BY_PERSON;
    }
}
