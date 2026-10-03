package com.sheout.staff.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random tokens (a console cookie, an invitation link, a CSRF token) and the
 * hash each is looked up by. Only the hash is ever stored: a copy of the
 * staff tables cannot be replayed as anybody's sign-in or invitation.
 * <p>
 * Plain SHA-256 is right here, unlike for passwords: these are 256 random
 * bits, so there is nothing to guess and no reason to make the lookup slow.
 */
final class StaffTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** No 0/o/1/l/i: a recovery code is read off paper and typed by hand. */
    private static final char[] RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();

    private StaffTokens() {
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time, so how much of a guessed CSRF token was right takes the same time to refuse. */
    static boolean same(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** "k7m2p-q9xht": ten characters from a 31-letter alphabet, about 50 bits. */
    static String newRecoveryCode() {
        StringBuilder code = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                code.append('-');
            }
            code.append(RECOVERY_ALPHABET[RANDOM.nextInt(RECOVERY_ALPHABET.length)]);
        }
        return code.toString();
    }

    /** What was typed, as it is hashed: no spaces or dashes, lower case. */
    static String normaliseRecoveryCode(String typed) {
        return typed == null ? "" : typed.replaceAll("[\\s-]", "").toLowerCase(java.util.Locale.ROOT);
    }
}
