package com.sheout.booking.internal;

import java.security.SecureRandom;

/**
 * The four-digit code a rider reads to her partner at the kerb.
 * <p>
 * The point of it is that possession of the code proves presence. A partner
 * who cannot produce it is not standing next to the person who booked, and
 * the trip does not start - which is what stops a trip being started, and a
 * fare charged, for a rider who was never collected.
 * <p>
 * Kept as a separate type rather than a couple of helpers on the service
 * because generation, comparison and the attempt limit are one decision.
 * Splitting them across the service would let a future caller compare codes
 * without counting the attempt.
 */
final class PickupCode {

    /**
     * Not {@link java.util.Random}. The codes are short-lived and low-value,
     * but they are still a secret that gates money, and a predictable
     * sequence would let one partner derive the next rider's code from her
     * own. SecureRandom costs nothing at this rate.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int DIGITS = 4;
    private static final int BOUND = 10_000;

    /**
     * How many wrong guesses before the code stops being accepted at all.
     * <p>
     * Five, because a rider reading four digits off a screen in the dark and
     * a partner typing them on a phone is a process that goes wrong honestly
     * more than once. Also because five of ten thousand is not a meaningful
     * fraction: a partner who wants to start a trip she should not start
     * gains essentially nothing by guessing.
     * <p>
     * A locked booking is not a stuck one. The partner can still cancel, and
     * either side can still reach support - the only thing she cannot do is
     * start a trip she has not proved she is at.
     */
    static final int MAX_ATTEMPTS = 5;

    private PickupCode() {
    }

    /**
     * A fresh code, always exactly four characters.
     * <p>
     * Left-padded, so 42 becomes "0042". Dropping the leading zeros would
     * mean the rider's screen and the partner's keypad disagree about how
     * many digits there are, which reads as a broken app to both of them.
     */
    static String generate() {
        return String.format("%0" + DIGITS + "d", RANDOM.nextInt(BOUND));
    }

    /**
     * Whether what the partner typed is the code on the booking.
     * <p>
     * Whitespace-tolerant on the submitted side, because a keypad and a
     * copy-paste both produce it and neither is a wrong answer. Nothing else
     * is normalised: this is four digits, not a name.
     * <p>
     * The comparison is length-independent and does not short-circuit on the
     * first differing character. Timing is a genuinely marginal concern for a
     * four-digit code guarded by an attempt limit, but constant-time
     * comparison is one line here and reasoning about whether it matters
     * costs more than doing it.
     */
    static boolean matches(String expected, String submitted) {
        if (expected == null || submitted == null) {
            return false;
        }
        String cleaned = submitted.trim();
        if (cleaned.length() != expected.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < expected.length(); i++) {
            difference |= expected.charAt(i) ^ cleaned.charAt(i);
        }
        return difference == 0;
    }

    /** Four digits and nothing else - checked before a guess is counted against the limit. */
    static boolean isWellFormed(String submitted) {
        if (submitted == null) {
            return false;
        }
        String cleaned = submitted.trim();
        if (cleaned.length() != DIGITS) {
            return false;
        }
        for (int i = 0; i < cleaned.length(); i++) {
            if (cleaned.charAt(i) < '0' || cleaned.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
