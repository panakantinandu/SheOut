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

    /** A police decision was attempted before the ID check passed. */
    ID_CHECK_NOT_PASSED,

    /** A decision other than VERIFIED/REJECTED was passed to a review call. */
    INVALID_DECISION,

    // ---- partner documents (licence, vehicle papers, police evidence)

    /** She tried to upload something only an operator or a provider puts on file - a background report. */
    DOCUMENT_TYPE_NOT_UPLOADABLE,

    /** No file came with the document. Nothing is approved without the evidence itself. */
    DOCUMENT_FILE_MISSING,

    /** A licence, RC, policy, certificate: its number is what an operator checks against the image. */
    DOCUMENT_NUMBER_REQUIRED,

    /** A document that expires was sent, or approved, without the date it expires on. */
    VALID_UNTIL_REQUIRED,

    /** The valid-until date has already passed, or comes before the issue date. */
    DOCUMENT_ALREADY_EXPIRED,

    /** An issue date in the future. */
    ISSUE_DATE_IN_FUTURE,

    /** A policy sent without saying whether it is for commercial or private use ("not sure" is an answer). */
    INSURANCE_USE_TYPE_REQUIRED,

    /** An operator tried to approve a private or unknown-use policy for a partner who carries passengers. */
    INSURANCE_NOT_COMMERCIAL,

    DOCUMENT_NOT_FOUND,

    /** A decision on a document that is not waiting for one - already decided, or replaced. */
    DOCUMENT_NOT_UNDER_REVIEW,

    /** Turning something down needs the reason she will be shown. */
    REASON_REQUIRED,

    /** Partner documents were sent from an account that is not a partner's. */
    NOT_A_PARTNER,

    // ---- consent and police verification with evidence

    /** She has not agreed to the current verification consent, and nothing is taken or checked until she does. */
    CONSENT_REQUIRED,

    /** She agreed to wording that is no longer the current version. */
    CONSENT_VERSION_OUTDATED,

    /**
     * A police check marked VERIFIED without how it was done, the
     * certificate number, its issue date, or the evidence document. Police
     * verification is a recorded fact with evidence behind it, not a button.
     */
    POLICE_EVIDENCE_MISSING,

    /** The evidence named is not hers, not a police certificate or report, has no file, or was turned down. */
    POLICE_EVIDENCE_INVALID,

    /**
     * A private background check offered as the only evidence while
     * POLICE_ACCEPT_THIRD_PARTY_BGV_ALONE is false - see PoliceVerificationRules.
     */
    BGV_NOT_SUFFICIENT_ALONE,

    /** The certificate is old enough that it would already be due for re-verification. */
    POLICE_CERTIFICATE_TOO_OLD,

    /** A background check was requested from a provider while none is configured. */
    PROVIDER_NOT_CONFIGURED
}
