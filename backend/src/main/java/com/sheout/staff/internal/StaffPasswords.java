package com.sheout.staff.internal;

import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Staff passwords: what is allowed, and how they are kept.
 * <p>
 * THE RULES are NIST SP 800-63B's, not a composition checklist: at least 12
 * characters, any characters at all, not one of the passwords attackers try
 * first, and no forced change every 90 days (which only teaches people to add
 * a number at the end). "Not common" also refuses a common password with
 * digits or symbols tacked on - "Password@2026" is "password".
 * <p>
 * HASHING is Argon2id, with OWASP's minimum parameters (19 MiB, 2 passes,
 * one lane): around a tenth of a second on the production instance, which is
 * nothing for a person signing in a few times a day and ruinous for anyone
 * guessing offline from a copied table. The encoded hash carries its own
 * parameters and salt, so they can be raised later without breaking existing
 * hashes.
 */
@Component
class StaffPasswords {

    static final int MIN_LENGTH = 12;
    /** Argon2 hashes whatever it is given; a megabyte "password" is a way to burn CPU. */
    static final int MAX_LENGTH = 128;

    private static final Pattern TRAILING_DIGITS_AND_SYMBOLS = Pattern.compile("[\\d\\p{Punct}\\s]+$");
    private static final Pattern LEADING_DIGITS_AND_SYMBOLS = Pattern.compile("^[\\d\\p{Punct}\\s]+");

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
    private final Set<String> common;
    /**
     * A real hash of nothing anybody knows. Checked against when the email is
     * not on the staff list, so "no such person" takes as long as "wrong
     * password" and the time a refusal takes says nothing.
     */
    private final String decoyHash;

    StaffPasswords() {
        this.common = load();
        this.decoyHash = encoder.encode("decoy-" + System.nanoTime());
    }

    /** Why this password is not allowed, or empty when it is. Plain sentences: the console shows them as they are. */
    Optional<String> problemWith(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Optional.of("Use at least " + MIN_LENGTH + " characters. A few unrelated words is easy to remember and hard to guess.");
        }
        if (password.length() > MAX_LENGTH) {
            return Optional.of("Use at most " + MAX_LENGTH + " characters.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (password.chars().distinct().count() < 4) {
            return Optional.of("This password repeats too few characters. Choose something less predictable.");
        }
        if (isCommon(lower)) {
            return Optional.of("This password is on the list of passwords attackers try first. Choose another.");
        }
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (lower.contains(email.toLowerCase(Locale.ROOT)) || (local.length() >= 4 && lower.contains(local))) {
                return Optional.of("Don't use your email address in your password.");
            }
        }
        return Optional.empty();
    }

    boolean isCommon(String lower) {
        if (common.contains(lower)) {
            return true;
        }
        String stripped = LEADING_DIGITS_AND_SYMBOLS.matcher(TRAILING_DIGITS_AND_SYMBOLS.matcher(lower).replaceAll(""))
                .replaceAll("");
        return stripped.length() >= 4 && common.contains(stripped);
    }

    String hash(String password) {
        return encoder.encode(password);
    }

    boolean matches(String password, String hash) {
        if (password == null || password.length() > MAX_LENGTH || hash == null || !hash.startsWith("$argon2")) {
            return false;
        }
        return encoder.matches(password, hash);
    }

    /** Spends the time a real check would, for an email nobody on the staff has. */
    void matchDecoy(String password) {
        encoder.matches(password == null ? "" : password.substring(0, Math.min(password.length(), MAX_LENGTH)), decoyHash);
    }

    private static Set<String> load() {
        Set<String> words = new HashSet<>(60_000);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("staff/common-passwords.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    words.add(line.trim());
                }
            }
        } catch (IOException e) {
            // Refuse to start rather than quietly accept "password123456".
            throw new IllegalStateException("Cannot read staff/common-passwords.txt", e);
        }
        return words;
    }
}
