package com.sheout.driververification.internal;

public enum VerificationError {

    /** No verification record exists yet for this account (AccountRegistered hasn't been processed, or bad accountId). */
    RECORD_NOT_FOUND,

    /** Document upload failed at the storage layer. */
    STORAGE_FAILED,

    /** Gender review attempted while status isn't UNDER_REVIEW (must be submitted first). */
    NOT_UNDER_REVIEW,

    /** Police review attempted on a CUSTOMER account - not applicable. */
    POLICE_VERIFICATION_NOT_APPLICABLE,

    /** A decision other than VERIFIED/REJECTED was passed to a review call. */
    INVALID_DECISION
}
