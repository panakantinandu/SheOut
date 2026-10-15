package com.sheout.driververification.internal;

/** Why a court-screening step was refused. */
public enum CourtScreeningError {
    /** The feature is off, or there is no such screening for this partner - the same answer, so neither leaks. */
    NOT_FOUND,
    /** Only partners are screened. */
    NOT_A_PARTNER,
    /** A screening is already open for her. */
    ALREADY_OPEN,
    /** Nothing may be searched or decided before she agrees to the current wording. */
    CONSENT_REQUIRED,
    /** She agreed to different wording from the version in force. */
    CONSENT_VERSION_OUTDATED,
    /** The step does not apply in the screening's current state. */
    WRONG_STATE,
    /** Anything but "No match" needs a note. */
    NOTE_REQUIRED,
    /** A search needs a district and the name variant used. */
    SEARCH_INCOMPLETE,
    /** The second reviewer must be someone other than the one who flagged the match. */
    SAME_REVIEWER,
    /** Nobody decides a case they flagged themselves. */
    OWN_FLAGGED_CASE,
    /** A flagged case needs a second reviewer's sign-off first. */
    SECOND_SIGNOFF_REQUIRED,
    /** Before a rejection she must be told and given until the response date to answer. */
    PARTNER_NOT_HEARD,
    /** A rejection needs a reason category. */
    REASON_REQUIRED,
    /** Approval needs at least one completed search (not only "could not search"). */
    NO_COMPLETED_SEARCH,
    /** Recording a police clearance certificate needs its date and issuing authority. */
    PCC_DETAILS_REQUIRED,
    /** The screenshot is not an image, or too large. */
    SCREENSHOT_INVALID
}
