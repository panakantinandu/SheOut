package com.sheout.driververification;

/** Where a partner's court-record screening stands. */
public enum CourtScreeningStatus {
    /** A reviewer asked; the partner has not agreed to the search yet. Nothing is searched before she does. */
    CONSENT_REQUESTED,
    /** She agreed; reviewers are searching and logging each search. */
    SEARCHING,
    /** A search found a possible or confirmed match: a second, different reviewer must look. */
    MATCH_TO_CONFIRM,
    /** The second reviewer agreed it may be her. She must be told and allowed to answer before any rejection. */
    MATCH_CONFIRMED,
    /** She has been told, and has until response_due_at to answer. */
    AWAITING_RESPONSE,
    /** She answered. */
    RESPONSE_RECEIVED,
    /** She was asked for a police clearance certificate. */
    AWAITING_PCC,
    APPROVED,
    APPROVED_WITH_NOTE,
    REJECTED,
    /** She withdrew her consent: the search stops and nothing more is recorded. */
    WITHDRAWN;

    public boolean decided() {
        return this == APPROVED || this == APPROVED_WITH_NOTE || this == REJECTED;
    }
}
