package com.sheout.marketplace.internal;

import java.util.regex.Pattern;

/**
 * A seller's contact number, kept as the ten digits of an Indian mobile
 * number. "+91 98765 43210", "09876543210" and "9876543210" are all the
 * same number; anything else is refused, rather than stored for a customer
 * to find it does not work.
 */
final class IndianMobile {

    private static final Pattern MOBILE = Pattern.compile("[6-9]\\d{9}");

    private IndianMobile() {
    }

    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return MOBILE.matcher(digits).matches() ? digits : null;
    }
}
