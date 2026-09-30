package com.sheout.driververification;

import java.time.Instant;

/**
 * Where a partner's start-of-shift safety check stands, for the modules that
 * act on it: users (may she go online) and dispatch (may she be offered a
 * trip, and what her rider is told).
 * <p>
 * valid is the only field anything should gate on. It is true when a check
 * passed (or an operator cleared one) inside the validity window - and
 * always true when the check is switched off, so a caller never has to know
 * about the switch.
 *
 * @param valid             she may work now
 * @param underReview       her last attempts did not match and an operator has
 *                          not looked yet; nothing she does will change that
 * @param checkedAt         when the check that makes her valid was taken
 * @param validUntil        when it stops counting
 * @param faceMatched       her phone matched the selfie to the one on file -
 *                          false when it could not tell (no photo on file, an
 *                          old phone) and the check passed on the photo alone
 * @param helmetPhotoOnFile a helmet photo came with the check
 */
public record ShiftCheckState(boolean valid,
                              boolean underReview,
                              Instant checkedAt,
                              Instant validUntil,
                              boolean faceMatched,
                              boolean helmetPhotoOnFile) {

    public static ShiftCheckState notRequired() {
        return new ShiftCheckState(true, false, null, null, false, false);
    }
}
