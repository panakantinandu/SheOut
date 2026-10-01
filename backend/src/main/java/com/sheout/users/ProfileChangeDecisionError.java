package com.sheout.users;

/** Why an operator's decision on a partner's profile change could not be recorded. */
public enum ProfileChangeDecisionError {
    /** No pending change with that id - already decided, withdrawn, or never existed. */
    CHANGE_NOT_FOUND,
    /** A vehicle change cannot be approved before its new registration certificate is in. */
    RC_DOCUMENT_REQUIRED,
    /** Turning a change down needs the reason she will be shown. */
    DECISION_NOTE_REQUIRED,
    PROFILE_NOT_FOUND
}
