package com.sheout.staff.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaffPasswordsTest {

    private final StaffPasswords passwords = new StaffPasswords();

    @Test
    void twelveCharactersAtLeast() {
        assertThat(passwords.problemWith("short-pass1", null)).isPresent();
        assertThat(passwords.problemWith(null, null)).isPresent();
        assertThat(passwords.problemWith("tamarind-kite-onboard-71", null)).isEmpty();
    }

    @Test
    void notOneAttackersTryFirstEvenWithSomethingTackedOn() {
        assertThat(passwords.problemWith("password12345", null)).isPresent();
        assertThat(passwords.problemWith("Password@2026", null)).as("common word, digits and a symbol added").isPresent();
        assertThat(passwords.problemWith("qwertyuiop123", null)).isPresent();
        assertThat(passwords.problemWith("SheOut@123456", null)).as("the product's own name").isPresent();
        assertThat(passwords.problemWith("123456789012", null)).isPresent();
    }

    @Test
    void notTheEmailAddressAndNotOneCharacterRepeated() {
        assertThat(passwords.problemWith("priya.reddy-2026!", "priya.reddy@example.com")).isPresent();
        assertThat(passwords.problemWith("aaaaaaaaaaaaaaaa", null)).isPresent();
        assertThat(passwords.problemWith("x".repeat(StaffPasswords.MAX_LENGTH + 1), null)).isPresent();
    }

    @Test
    void hashesAreArgon2idSaltedAndCheckable() {
        String hash = passwords.hash("tamarind-kite-onboard-71");
        assertThat(hash).startsWith("$argon2id$");
        assertThat(passwords.hash("tamarind-kite-onboard-71")).as("salted: same password, new hash").isNotEqualTo(hash);
        assertThat(passwords.matches("tamarind-kite-onboard-71", hash)).isTrue();
        assertThat(passwords.matches("tamarind-kite-onboard-72", hash)).isFalse();
        assertThat(passwords.matches("anything", StaffMemberEntity.NO_PASSWORD)).as("a reset account").isFalse();
    }

    @Test
    void recoveryCodesAreLongRandomAndForgivingToType() {
        String code = StaffTokens.newRecoveryCode();
        assertThat(code).matches("[a-z2-9]{5}-[a-z2-9]{5}");
        assertThat(StaffTokens.normaliseRecoveryCode(" " + code.toUpperCase().replace("-", " - ") + " "))
                .isEqualTo(code.replace("-", ""));
    }
}
