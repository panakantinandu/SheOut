package com.sheout.insurance;

/**
 * A partner's place in a group cover. PENDING_ENROLMENT until the insurer
 * confirms her membership and an operator records it; only ENROLLED is ever
 * shown to her as cover she has.
 */
public enum EnrolmentStatus {
    PENDING_ENROLMENT,
    ENROLLED,
    EXITED
}
