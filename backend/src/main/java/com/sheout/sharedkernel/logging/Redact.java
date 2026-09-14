package com.sheout.sharedkernel.logging;

import java.util.regex.Pattern;

/**
 * Masks personal data before it reaches a log line.
 * <p>
 * Logs here are read by more people, kept longer, and copied to more places
 * than the database: a hosting dashboard, a support thread, a pasted stack
 * trace. A phone number that lands in one has left the access controls
 * that protect it everywhere else. Masking keeps enough to correlate two
 * lines about the same number - the country code and the last two digits -
 * and nothing that identifies whose number it is.
 */
public final class Redact {

    /** An E.164-ish number: optional +, then 8-15 digits with optional spaces or dashes. */
    private static final Pattern PHONE_IN_TEXT = Pattern.compile("\\+?\\d[\\d \\-]{6,17}\\d");

    private Redact() {
    }

    /** "+919876543210" becomes "+91********10". Null-safe. */
    public static String phone(String phoneNumber) {
        if (phoneNumber == null) {
            return "null";
        }
        String digits = phoneNumber.strip();
        if (digits.length() <= 5) {
            return "*".repeat(digits.length());
        }
        return digits.substring(0, 3) + "*".repeat(digits.length() - 5) + digits.substring(digits.length() - 2);
    }

    /** Masks every phone-number-shaped run inside free text, e.g. a provider's error message. */
    public static String phoneNumbersIn(String text) {
        if (text == null) {
            return null;
        }
        return PHONE_IN_TEXT.matcher(text).replaceAll(match -> phone(match.group()));
    }
}
