package com.sheout.driververification;

/**
 * One thing the automatic checks noticed about a check's subject.
 * <p>
 * {@code fails} findings set the check to FAILED_AUTO; the rest only ask a
 * person to look (NEEDS_REVIEW). {@code overridable} says whether a person
 * may accept a FAILED_AUTO check anyway, with a written note and a second
 * person's sign-off. An expired document and an applicant under 18 cannot be
 * overridden: the fix is a renewed document, or a corrected date of birth.
 * <p>
 * Name findings never fail. Indian names are written many ways (Laxmi and
 * Lakshmi, initials, either order), so a difference is a question for a
 * person, never a verdict.
 * <p>
 * {@code text} is one plain line for the reviewer. What the applicant is
 * told comes from the rejection reason a person picks, never from here.
 * <p>
 * Stored by name; never rename a value while rows carry it.
 */
public enum CheckFinding {
    UNDER_18(true, false, "Under 18 by the date of birth on her profile."),
    DATE_OF_BIRTH_MISSING(false, false, "No date of birth on her profile. Read it off her ID."),
    DOCUMENT_EXPIRED(true, false, "The expiry date she gave has passed."),
    NAME_MISMATCH(false, false, "The name she typed for this document is not the name on her profile."),
    NAME_DECLARED(false, false, "She said the name is the same as on her profile. Compare it with the document."),
    DUPLICATE_NUMBER(false, false, "The same number is on another account."),
    PREVIOUSLY_REJECTED(true, true, "This number was turned down before, for fraud or a confirmed mismatch."),
    PLATE_MISMATCH(false, false, "The registration number is not the one on her profile."),
    PHOTO_QUALITY(false, false, "Her phone said the photo may be unclear, and she chose to send it anyway."),
    NOT_COMMERCIAL(false, false, "She said the policy is for private use. Riders need a commercial policy.");

    private final boolean fails;
    private final boolean overridable;
    private final String text;

    CheckFinding(boolean fails, boolean overridable, String text) {
        this.fails = fails;
        this.overridable = overridable;
        this.text = text;
    }

    public boolean fails() {
        return fails;
    }

    public boolean overridable() {
        return overridable;
    }

    public String text() {
        return text;
    }
}
