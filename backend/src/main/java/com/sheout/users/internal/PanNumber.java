package com.sheout.users.internal;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A Permanent Account Number, as the Income Tax Department issues them:
 * five letters, four digits, one letter.
 * <p>
 * Checked here for shape only. Nothing in this codebase can tell whether a
 * well-formed PAN actually belongs to the partner who typed it - that is the
 * tax department's record, not ours - so this rejects typos and nothing else.
 * It is not, and must not become, a second identity check: who a partner is
 * gets established by an operator reading her Aadhaar.
 * <p>
 * Stored upper-cased with spaces stripped, so one number has one spelling and
 * a future lookup by it cannot miss.
 */
final class PanNumber {

    /** AAAAA9999A. The fourth letter encodes the holder type and the fifth their surname initial; neither is checked. */
    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    private PanNumber() {
    }

    /** Blank is a valid answer - the field is optional. */
    static boolean isValidOrBlank(String pan) {
        return pan == null || pan.isBlank() || PAN.matcher(normalize(pan)).matches();
    }

    /** Upper-cased, with spaces and dashes removed. Null and blank both become null - "no PAN on file". */
    static String normalize(String pan) {
        if (pan == null) {
            return null;
        }
        String cleaned = pan.replaceAll("[\s-]", "").toUpperCase(Locale.ROOT);
        return cleaned.isEmpty() ? null : cleaned;
    }
}
