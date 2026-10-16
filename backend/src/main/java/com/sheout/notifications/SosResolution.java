package com.sheout.notifications;

/**
 * What the operator found when she closed an SOS - recorded with it, so a
 * false alarm and a call to the police do not look the same afterwards.
 * The steps that lead to each are in docs/sos-runbook.md.
 */
public enum SosResolution {
    /** She was reached and is safe; the press was a mistake. */
    FALSE_ALARM,
    /** She was reached, something had happened, and she is safe now. */
    SAFE_CONFIRMED,
    /** She could not be reached, but an emergency contact got to her and confirmed she is safe. */
    CONTACT_CONFIRMED_SAFE,
    /** The police (112) were called or are with her. */
    POLICE_INVOLVED,
    /** Anything else - the note says what. */
    OTHER
}
