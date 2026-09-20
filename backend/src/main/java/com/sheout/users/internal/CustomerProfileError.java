package com.sheout.users.internal;

public enum CustomerProfileError {
    PROFILE_NOT_FOUND,
    CONTACT_NOT_FOUND,
    PHOTO_STORAGE_FAILED,
    PROFILE_PHOTO_REQUIRED,
    DATE_OF_BIRTH_REQUIRED,
    INVALID_DATE_OF_BIRTH,
    UNDER_MINIMUM_AGE,
    INVALID_EMAIL,

    /**
     * The profile is being completed for the first time and nothing on this
     * account records agreeing to the Terms and Privacy Policy. The sign-up
     * screen asks for it with an unticked box; this is what stops a client
     * that skipped it.
     */
    CONSENT_REQUIRED
}
