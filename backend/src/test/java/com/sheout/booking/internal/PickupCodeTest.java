package com.sheout.booking.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the code that decides whether a trip may start.
 * <p>
 * The leading-zero case is the one worth guarding hardest. A code stored or
 * formatted as a number turns "0042" into "42", and the failure is not a
 * crash - it is a rider reading four digits off her screen while her
 * partner's keypad rejects them, at a kerb, with no way for either of them
 * to tell whose fault it is.
 */
class PickupCodeTest {

    @Test
    @DisplayName("every generated code is exactly four digits, leading zeros kept")
    void generatesFourDigits() {
        // Enough draws that a code below 1000 is a near-certainty: the odds
        // of never seeing one in 2000 tries are about 1 in 10^91.
        for (int i = 0; i < 2000; i++) {
            String code = PickupCode.generate();
            assertEquals(4, code.length(), "code was " + code);
            assertTrue(code.chars().allMatch(Character::isDigit), "code was " + code);
        }
    }

    @Test
    @DisplayName("generated codes are not all the same value")
    void generatesVaryingCodes() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(PickupCode.generate());
        }
        // A generator stuck on one value is the failure this catches; the
        // bar is deliberately far below what real randomness produces.
        assertTrue(seen.size() > 50, "only " + seen.size() + " distinct codes in 200 draws");
    }

    @Test
    @DisplayName("the right code matches")
    void matchesCorrectCode() {
        assertTrue(PickupCode.matches("0042", "0042"));
        assertTrue(PickupCode.matches("9999", "9999"));
    }

    @Test
    @DisplayName("surrounding whitespace is not a wrong answer")
    void tolerantOfWhitespace() {
        assertTrue(PickupCode.matches("1234", " 1234 "));
        assertTrue(PickupCode.matches("1234", "1234\n"));
    }

    @Test
    @DisplayName("a code that differs anywhere is refused")
    void refusesWrongCode() {
        assertFalse(PickupCode.matches("1234", "1235"));
        assertFalse(PickupCode.matches("1234", "2234"));
        // "42" must not pass for "0042" - the zeros are part of the secret,
        // not formatting.
        assertFalse(PickupCode.matches("0042", "42"));
        assertFalse(PickupCode.matches("1234", "12345"));
        assertFalse(PickupCode.matches("1234", ""));
    }

    @Test
    @DisplayName("a missing code on either side is refused, not a match")
    void refusesNulls() {
        assertFalse(PickupCode.matches(null, "1234"));
        assertFalse(PickupCode.matches("1234", null));
        assertFalse(PickupCode.matches(null, null));
    }

    @Test
    @DisplayName("well-formed means four digits and nothing else")
    void checksShape() {
        assertTrue(PickupCode.isWellFormed("0000"));
        assertTrue(PickupCode.isWellFormed(" 1234 "));
        assertFalse(PickupCode.isWellFormed("123"));
        assertFalse(PickupCode.isWellFormed("12345"));
        assertFalse(PickupCode.isWellFormed("12a4"));
        assertFalse(PickupCode.isWellFormed(""));
        assertFalse(PickupCode.isWellFormed(null));
        // A minus sign is four characters but not four digits, and would
        // otherwise burn one of a partner's five attempts.
        assertFalse(PickupCode.isWellFormed("-123"));
    }
}
