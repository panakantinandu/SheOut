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
    PHOTO_STORAGE_FAILED,

    /**
     * Requested ONLINE from somewhere SheOut does not operate.
     * <p>
     * Checked on where the PHONE is, not on any trip's pickup or drop,
     * because a partner is the vehicle: a partner in another country cannot
     * collect anybody in Hyderabad. This is deliberately NOT the rule for
     * customers - somebody abroad booking a ride for their mother here is a
     * real customer, and booking is gated on the trip's own two ends
     * instead. See ServiceArea.
     */
    OUTSIDE_SERVICE_AREA,

    /**
     * Requested ONLINE without saying where she is.
     * <p>
     * Going online means "offer me trips near me", which is not a question
     * that can be answered without a position. It used to be allowed, and
     * the result was a partner sitting on a screen reading "Looking for ride
     * requests nearby" while dispatch had no idea where nearby was.
     */
    LOCATION_REQUIRED,

    DATE_OF_BIRTH_REQUIRED,

    INVALID_DATE_OF_BIRTH,

    UNDER_MINIMUM_AGE,

    INVALID_EMAIL
}
