package com.sheout.users.internal;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The checks a profile has to pass that are the same for riders and partners.
 * <p>
 * THE AGE RULE. SheOut's Terms say nobody under 18 may use it, and until now
 * nothing checked. Age is worked out on today's date in India, where SheOut
 * operates: someone whose 18th birthday is today in Hyderabad is 18, even at
 * an hour when it is still yesterday on a server clock in UTC.
 */
final class ProfileRules {

    static final int MINIMUM_AGE = 18;

    /** Older than anyone alive; a year like 1850 is a typing mistake, not a customer. */
    static final int MAXIMUM_AGE = 120;

    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    /**
     * Deliberately loose - one @, something either side, a dot in the domain,
     * no spaces. The only real test of an email address is whether mail
     * arrives; a strict pattern rejects real addresses and accepts typos.
     */
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]{2,}$");

    private ProfileRules() {
    }

    enum BirthDateProblem {
        MISSING,
        IMPLAUSIBLE,
        UNDER_MINIMUM_AGE
    }

    static Optional<BirthDateProblem> checkDateOfBirth(LocalDate dateOfBirth, LocalDate today) {
        if (dateOfBirth == null) {
            return Optional.of(BirthDateProblem.MISSING);
        }
        if (dateOfBirth.isAfter(today) || Period.between(dateOfBirth, today).getYears() > MAXIMUM_AGE) {
            return Optional.of(BirthDateProblem.IMPLAUSIBLE);
        }
        if (Period.between(dateOfBirth, today).getYears() < MINIMUM_AGE) {
            return Optional.of(BirthDateProblem.UNDER_MINIMUM_AGE);
        }
        return Optional.empty();
    }

    static LocalDate todayInIndia() {
        return LocalDate.now(INDIA);
    }

    /** Blank means no email. Otherwise lower-cased and trimmed, or empty if it is not an email address. */
    static Optional<String> normaliseEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.of("");
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        return trimmed.length() <= 254 && EMAIL.matcher(trimmed).matches() ? Optional.of(trimmed) : Optional.empty();
    }
}
