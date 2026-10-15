package com.sheout.driververification;

/** What one search of the court records found. Anything but NO_MATCH needs a note. */
public enum CourtSearchOutcome {
    NO_MATCH,
    POSSIBLE_MATCH,
    CONFIRMED_MATCH,
    /** The site was down, the district not covered, the captcha would not load - said, not skipped. */
    COULD_NOT_SEARCH;

    public boolean isMatch() {
        return this == POSSIBLE_MATCH || this == CONFIRMED_MATCH;
    }
}
