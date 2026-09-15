package com.sheout.users.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the age rule the Terms promise. The boundary is the case that
 * matters: the day before an 18th birthday must be refused, the day itself
 * accepted.
 */
class ProfileRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    @Test
    @DisplayName("18 today is old enough; 18 tomorrow is not")
    void eighteenthBirthdayBoundary() {
        assertTrue(ProfileRules.checkDateOfBirth(LocalDate.of(2008, 9, 15), TODAY).isEmpty());
        assertEquals(Optional.of(ProfileRules.BirthDateProblem.UNDER_MINIMUM_AGE),
                ProfileRules.checkDateOfBirth(LocalDate.of(2008, 9, 16), TODAY));
    }

    @Test
    @DisplayName("a leap-day birthday turns 18 on 1 March in a non-leap year")
    void leapDayBirthday() {
        assertEquals(Optional.of(ProfileRules.BirthDateProblem.UNDER_MINIMUM_AGE),
                ProfileRules.checkDateOfBirth(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 2, 28)));
        assertTrue(ProfileRules.checkDateOfBirth(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 3, 1)).isEmpty());
    }

    @Test
    @DisplayName("missing, future and impossibly old dates are refused")
    void implausibleDates() {
        assertEquals(Optional.of(ProfileRules.BirthDateProblem.MISSING), ProfileRules.checkDateOfBirth(null, TODAY));
        assertEquals(Optional.of(ProfileRules.BirthDateProblem.IMPLAUSIBLE),
                ProfileRules.checkDateOfBirth(TODAY.plusDays(1), TODAY));
        assertEquals(Optional.of(ProfileRules.BirthDateProblem.IMPLAUSIBLE),
                ProfileRules.checkDateOfBirth(LocalDate.of(1850, 1, 1), TODAY));
    }

    @Test
    @DisplayName("email: blank means none, addresses are normalised, obvious non-addresses are refused")
    void email() {
        assertEquals(Optional.of(""), ProfileRules.normaliseEmail("  "));
        assertEquals(Optional.of("priya.s@gmail.com"), ProfileRules.normaliseEmail(" Priya.S@Gmail.com "));
        assertTrue(ProfileRules.normaliseEmail("priya@gmail").isEmpty());
        assertTrue(ProfileRules.normaliseEmail("priya gmail.com").isEmpty());
        assertTrue(ProfileRules.normaliseEmail("priya@@gmail.com").isEmpty());
    }
}
