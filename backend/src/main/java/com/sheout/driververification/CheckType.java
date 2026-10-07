package com.sheout.driververification;

/**
 * The checks a verification is made of. Each is one question a reviewer
 * answers, and each sits behind a CheckProvider, so a vendor can take one
 * over later without the workflow changing.
 * <p>
 * Stored by name; never rename a value while rows carry it.
 */
public enum CheckType {
    /** A government photo ID, read by a person: she is a woman, the name is hers, she is 18 or older. */
    IDENTITY,
    /** The live selfie is the same woman as the photo on the ID. */
    SELFIE_MATCH,
    /** Her driving licence: hers, for a two-wheeler, in date. Partners only. */
    LICENCE,
    /** The vehicle's RC: the plate on her profile, a two-wheeler, in date. Partners only. */
    VEHICLE,
    /** A commercial policy for that vehicle, in date. Partners only. */
    INSURANCE,
    /** Her PAN, for payouts. Optional, and never part of approval. */
    PAN,
    /** Her police check. Partners only. */
    BACKGROUND
}
