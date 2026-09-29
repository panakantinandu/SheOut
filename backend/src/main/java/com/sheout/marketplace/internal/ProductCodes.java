package com.sheout.marketplace.internal;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A product's short reference code, e.g. 7K9M2XQ.
 * <p>
 * Seven characters from 31: about 27.5 billion codes, so a collision is
 * rare, and the caller still checks and draws again. The alphabet leaves out
 * 0/O and 1/I/L, which look alike on a phone and sound alike on a call - the
 * code is meant to be read aloud and typed back. V43 generated the codes of
 * products that existed before this, from the same alphabet.
 */
final class ProductCodes {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 7;
    private static final Pattern SHAPE = Pattern.compile("[" + ALPHABET + "]{" + LENGTH + "}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private ProductCodes() {
    }

    static String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * What she typed, as a code, if it could be one: trimmed, upper-cased,
     * with a leading # dropped. Null for anything that is not code-shaped, so
     * an ordinary search word never costs a lookup by code.
     */
    static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String code = typed.trim().toUpperCase(Locale.ROOT);
        if (code.startsWith("#")) {
            code = code.substring(1);
        }
        return SHAPE.matcher(code).matches() ? code : null;
    }
}
