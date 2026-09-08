package com.sheout.driververification;

/**
 * Shared by both gender_verification_status and (for drivers)
 * police_verification_status.
 * <p>
 * ASSUMPTION FLAGGED: the spec described these as two slightly different
 * state machines - gender_verification_status as
 * PENDING/UNDER_REVIEW/VERIFIED/REJECTED, and the review queue as
 * SUBMITTED/UNDER_REVIEW/VERIFIED/REJECTED. Treated here as the same
 * 4-state machine: PENDING is the state before any document is submitted;
 * submitting a document moves it straight to UNDER_REVIEW (there is no
 * separate persisted SUBMITTED state - it collapses into UNDER_REVIEW,
 * since a submission is only meaningful once it's awaiting review).
 */
public enum VerificationStatus {
    PENDING,
    UNDER_REVIEW,
    VERIFIED,
    REJECTED
}
