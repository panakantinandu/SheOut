package com.sheout.users.internal;

public enum DriverProfileError {
    PROFILE_NOT_FOUND,

    /** Requested ONLINE but gender and/or police verification isn't VERIFIED yet. */
    NOT_VERIFIED
}
