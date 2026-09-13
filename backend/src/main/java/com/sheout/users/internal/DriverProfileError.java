package com.sheout.users.internal;

public enum DriverProfileError {
    PROFILE_NOT_FOUND,

    /** Requested ONLINE but gender and/or police verification isn't VERIFIED yet. */
    NOT_VERIFIED,

    /**
     * Requested ONLINE with no profile photo on file.
     * <p>
     * Separate from NOT_VERIFIED because it is a different problem with a
     * different fix, and the partner needs to be told which one she is
     * looking at. Verification means waiting for an operator; a missing
     * photo means taking one, which she can do in the next thirty seconds.
     * Collapsing them into "you cannot go online" would leave her waiting
     * on a review that was never the obstacle.
     */
    PROFILE_PHOTO_REQUIRED,

    /**
     * The registration number is not a well-formed Indian plate. The
     * message travels separately - see VehicleRegistrationNumber, which
     * says which of the two mistakes was made.
     */
    INVALID_REGISTRATION_NUMBER,

    /** The uploaded photo could not be stored. */
    PHOTO_STORAGE_FAILED
}
