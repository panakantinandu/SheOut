package com.sheout.staff.internal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Authenticator-app codes (RFC 6238 TOTP): six digits, a new one every 30
 * seconds, HMAC-SHA1 - the parameters every authenticator app (Google,
 * Microsoft, Authy, 1Password) uses without being told otherwise.
 * <p>
 * Written here rather than pulled in as a library because it is forty lines
 * of the RFC and the test checks it against the RFC's own vectors.
 * <p>
 * One step either side of now is accepted, for a phone clock a little out.
 * A code is accepted once: the caller stores the step it matched and passes
 * it back as {@code lastUsedStep}, and any code for that step or an earlier
 * one is refused - so a code read over someone's shoulder, or replayed from a
 * captured request, is worth nothing.
 */
final class Totp {

    static final int DIGITS = 6;
    static final long STEP_SECONDS = 30;
    private static final int WINDOW = 1;
    private static final int SECRET_BYTES = 20;
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** A new 160-bit secret, base32 as authenticator apps expect it. */
    static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    static long stepAt(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), STEP_SECONDS);
    }

    /**
     * The step this code matched, if it is right and newer than the last one
     * used. Every candidate step is computed and compared in constant time,
     * whether or not an earlier one matched.
     */
    static OptionalLong verify(String base32Secret, String code, Instant now, Long lastUsedStep) {
        if (code == null || code.length() != DIGITS || !code.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        byte[] key = fromBase32(base32Secret);
        long current = stepAt(now);
        long matched = -1;
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            boolean same = MessageDigest.isEqual(
                    codeAt(key, step).getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII));
            if (same && matched < 0) {
                matched = step;
            }
        }
        if (matched < 0 || (lastUsedStep != null && matched <= lastUsedStep)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(matched);
    }

    /** The code for one 30-second step (RFC 4226's HOTP of the step number). */
    static String codeAt(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * What the QR code holds. The issuer is in both the label and the
     * parameter, which is how every app agrees on what to call the entry.
     */
    static String otpauthUri(String issuer, String accountName, String base32Secret) {
        String label = encode(issuer) + ":" + encode(accountName);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32[(buffer << (5 - bits)) & 31]);
        }
        return out.toString();
    }

    static byte[] fromBase32(String text) {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = c >= 'A' && c <= 'Z' ? c - 'A' : c >= '2' && c <= '7' ? c - '2' + 26 : -1;
            if (value < 0) {
                throw new IllegalArgumentException("Not base32");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
