package com.sheout.sharedkernel.privacy;

import java.util.regex.Pattern;

/**
 * How each kind of personal data is shown when it is not revealed. One place,
 * so the console, the insurer's file and anything else agree on what
 * "masked" means.
 */
public final class Masking {

    /** Only digits, spaces, dashes, brackets and a leading plus: something a person would dial. */
    private static final Pattern DIALLABLE = Pattern.compile("^\\s*\\+?[\\d\\s()-]+$");
    private static final char DOT = '•';

    private Masking() {
    }

    /**
     * A person's phone number: 10 to 13 digits, mobile or landline, with or
     * without +91. A toll-free number (1800, 1860 - an insurer's helpline)
     * belongs to a business, not a person, and is left alone; so is anything
     * with letters in it, like a staff member's name in a "byPhone" field.
     */
    public static boolean looksLikePersonalPhone(String value) {
        if (value == null || !DIALLABLE.matcher(value).matches()) {
            return false;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.startsWith("1800") || digits.startsWith("1860")) {
            return false;
        }
        return digits.length() >= 10 && digits.length() <= 13;
    }

    /** "+910000012345" -> "00•••••345": enough to tell two people apart, not enough to call. */
    public static String phone(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        if (digits.length() < 6) {
            return String.valueOf(DOT).repeat(5);
        }
        return digits.substring(0, 2) + String.valueOf(DOT).repeat(digits.length() - 5) + digits.substring(digits.length() - 3);
    }

    /**
     * The locality and city of an address, without the house: the last two
     * comma-separated parts that have no digit in them (a flat, a plot, a road
     * number and a PIN code all do). A heuristic, good for free text from a
     * geocoder or typed by her; never the full address.
     */
    public static String area(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String[] parts = label.split(",");
        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (int i = parts.length - 1; i >= 0 && kept < 2; i--) {
            String part = parts[i].trim();
            if (part.isEmpty() || part.chars().anyMatch(Character::isDigit) || part.equalsIgnoreCase("India")) {
                continue;
            }
            out.insert(0, kept == 0 ? part : part + ", ");
            kept++;
        }
        String area = out.toString();
        return area.isEmpty() ? null : area.length() > 200 ? area.substring(0, 200) : area;
    }

    /** About a kilometre: two decimal places of a degree. */
    public static double coordinate(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** "ABCDE1234F" -> "•••••1234F". */
    public static String pan(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 5 ? String.valueOf(DOT).repeat(value.length())
                : String.valueOf(DOT).repeat(5) + value.substring(5);
    }

    /** An account number or UPI id: its last four characters. */
    public static String bank(String value) {
        if (value == null) {
            return null;
        }
        String visible = value.length() <= 4 ? "" : value.substring(value.length() - 4);
        return String.valueOf(DOT).repeat(4) + visible;
    }
}
