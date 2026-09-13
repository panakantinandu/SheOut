package com.sheout.users.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what counts as a real Indian plate.
 * <p>
 * The state-code list is the half worth guarding. A format-only regex looks
 * like it is validating something and accepts ZZ01AB1234 quite happily, and
 * nothing downstream would ever notice - an operator glancing at the review
 * queue sees a plausible-looking number and moves on.
 */
class VehicleRegistrationNumberTest {

    @Test
    @DisplayName("accepts real plates in the standard format")
    void acceptsStandardPlates() {
        assertTrue(VehicleRegistrationNumber.isValid("TS06FH2653"));
        assertTrue(VehicleRegistrationNumber.isValid("KA05MH1234"));
        assertTrue(VehicleRegistrationNumber.isValid("DL3CAB1234"));
        assertTrue(VehicleRegistrationNumber.isValid("MH12DE1433"));
        // Single-digit RTO code and single-letter series both occur.
        assertTrue(VehicleRegistrationNumber.isValid("TN7B1234"));
    }

    @Test
    @DisplayName("accepts the BH series, which carries no state code by design")
    void acceptsBharatSeries() {
        assertTrue(VehicleRegistrationNumber.isValid("22BH1234AB"));
        assertTrue(VehicleRegistrationNumber.isValid("21BH9999A"));
    }

    @Test
    @DisplayName("a plausible shape with an invented state code is still refused")
    void refusesFakeStateCodes() {
        // The case a format-only regex misses entirely.
        assertFalse(VehicleRegistrationNumber.isValid("ZZ01AB1234"));
        assertFalse(VehicleRegistrationNumber.isValid("XY12CD3456"));
        assertTrue(VehicleRegistrationNumber.rejectionReason("ZZ01AB1234").contains("not an Indian state code"));
    }

    @Test
    @DisplayName("refuses anything that is not plate-shaped")
    void refusesGarbage() {
        assertFalse(VehicleRegistrationNumber.isValid("ABC123"));
        assertFalse(VehicleRegistrationNumber.isValid(""));
        assertFalse(VehicleRegistrationNumber.isValid(null));
        assertFalse(VehicleRegistrationNumber.isValid("TS06FH265"));   // three trailing digits
        assertFalse(VehicleRegistrationNumber.isValid("TS06FH26533")); // five
        assertFalse(VehicleRegistrationNumber.isValid("TSFH2653"));    // no RTO code
    }

    @Test
    @DisplayName("legacy state codes still on the road are accepted")
    void acceptsLegacyStateCodes() {
        // Refusing these would lock a partner out of working over a code
        // change she had nothing to do with - see the STATE_CODES comment.
        assertTrue(VehicleRegistrationNumber.isValid("OR02AB1234")); // Odisha, before OD
        assertTrue(VehicleRegistrationNumber.isValid("UA07BC1234")); // Uttarakhand, before UK
        assertTrue(VehicleRegistrationNumber.isValid("TS09AB1234")); // Telangana, before TG
        assertTrue(VehicleRegistrationNumber.isValid("TG09AB1234")); // and after
    }

    @Test
    @DisplayName("how a person writes a plate is not a reason to refuse it")
    void normalizesSpacingAndCase() {
        assertTrue(VehicleRegistrationNumber.isValid("ts 06 fh 2653"));
        assertTrue(VehicleRegistrationNumber.isValid("TS-06-FH-2653"));
        assertEquals("TS06FH2653", VehicleRegistrationNumber.normalize("ts 06 fh 2653"));
        assertEquals("TS06FH2653", VehicleRegistrationNumber.normalize("TS-06-FH-2653"));
    }

    @Test
    @DisplayName("the refusal says which mistake was made")
    void reasonsAreSpecific() {
        assertTrue(VehicleRegistrationNumber.rejectionReason("").contains("Enter your vehicle"));
        assertTrue(VehicleRegistrationNumber.rejectionReason("ABC123").contains("format on your number plate"));
        // A wrong state code and a wrong shape need different corrections,
        // so they must not produce the same sentence.
        assertFalse(VehicleRegistrationNumber.rejectionReason("ZZ01AB1234")
                .equals(VehicleRegistrationNumber.rejectionReason("ABC123")));
    }
}
