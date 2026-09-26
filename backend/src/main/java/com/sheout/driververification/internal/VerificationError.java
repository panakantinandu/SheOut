package com.sheout.driververification.internal;

public enum VerificationError {

    /** No verification record exists yet for this account (AccountRegistered hasn't been processed, or bad accountId). */
    RECORD_NOT_FOUND,

    /** Document upload failed at the storage layer. */
    STORAGE_FAILED,

    /**
     * A partner submitted without the vehicle registration certificate.
     * <p>
     * Required for partners only - a rider has no vehicle to produce one
     * for. Without it an operator has nothing to check the typed
     * registration number against, which is the whole reason that check
     * exists.
     */
    RC_DOCUMENT_REQUIRED,

    /** Not a photo or a PDF - see DocumentRules. */
    DOCUMENT_TYPE_UNSUPPORTED,

    /** Too small to read a name or a face from. */
    DOCUMENT_TOO_SMALL,

    /** Larger than the server accepts. */
    DOCUMENT_TOO_LARGE,

    /** Submitted without the live selfie, or with one that is not a readable photo. */
    SELFIE_REQUIRED,

    /** The selfie does not answer the latest challenge issued to this account, or answers it too late. */
    SELFIE_CHALLENGE_EXPIRED,

    /** Identity already VERIFIED - the approved document is not replaced by a new upload. */
    ALREADY_VERIFIED,

    /** Gender review attempted while status isn't UNDER_REVIEW (must be submitted first). */
    NOT_UNDER_REVIEW,

    /** Police review attempted on a CUSTOMER account - not applicable. */
    POLICE_VERIFICATION_NOT_APPLICABLE,

    /** A decision other than VERIFIED/REJECTED was passed to a review call. */
    INVALID_DECISION
}
