package com.sheout.assistant.internal;

import java.util.regex.Pattern;

/**
 * Masks phone numbers before text reaches a log: every run of eight or more
 * digits (with the spaces, dashes, dots and brackets people type inside
 * phone numbers, and an optional +country code) keeps its last two digits.
 * "+91 98765 43210" becomes "+*********10".
 * <p>
 * Deliberately broad. It also catches order ids and long numbers that are
 * not phones, which costs a little log detail. A phone number that reaches a
 * log costs a woman her privacy.
 */
final class PhoneMasking {

    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s().-]{6,}\\d");

    private PhoneMasking() {
    }

    static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return PHONE.matcher(text).replaceAll(match -> {
            String digits = match.group().replaceAll("\\D", "");
            if (digits.length() < 8) {
                return match.group();
            }
            String kept = digits.substring(digits.length() - 2);
            return (match.group().startsWith("+") ? "+" : "") + "*".repeat(digits.length() - 2) + kept;
        });
    }
}
