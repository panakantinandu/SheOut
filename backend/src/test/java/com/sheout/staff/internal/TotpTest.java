package com.sheout.staff.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Authenticator codes against RFC 6238's own test vectors, and the one-use rule. */
class TotpTest {

    /** RFC 6238 appendix B: the SHA-1 key is the ASCII bytes "12345678901234567890". */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfcVectors() {
        // The RFC prints eight digits; an authenticator shows the last six.
        assertThat(Totp.codeAt(RFC_KEY, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.codeAt(RFC_KEY, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.codeAt(RFC_KEY, 1111111111L / 30)).isEqualTo("050471");
        assertThat(Totp.codeAt(RFC_KEY, 1234567890L / 30)).isEqualTo("005924");
        assertThat(Totp.codeAt(RFC_KEY, 2000000000L / 30)).isEqualTo("279037");
        assertThat(Totp.codeAt(RFC_KEY, 20000000000L / 30)).isEqualTo("353130");
    }

    @Test
    void base32RoundTripsAndIsWhatAppsExpect() {
        // "12345678901234567890" in base32, as authenticator apps would be given it.
        assertThat(Totp.base32(RFC_KEY)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.fromBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")).isEqualTo(RFC_KEY);
        String secret = Totp.newSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
    }

    @Test
    void acceptsOneStepEitherSideAndNothingFurther() {
        String secret = Totp.base32(RFC_KEY);
        Instant now = Instant.ofEpochSecond(1234567890L);
        long step = Totp.stepAt(now);
        assertThat(Totp.verify(secret, Totp.codeAt(RFC_KEY, step), now, null)).hasValue(step);
        assertThat(Totp.verify(secret, Totp.codeAt(RFC_KEY, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(secret, Totp.codeAt(RFC_KEY, step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.verify(secret, Totp.codeAt(RFC_KEY, step - 2), now, null)).isEmpty();
        assertThat(Totp.verify(secret, Totp.codeAt(RFC_KEY, step + 2), now, null)).isEmpty();
    }

    @Test
    void aCodeWorksOnceAndNeverAfterANewerOne() {
        String secret = Totp.base32(RFC_KEY);
        Instant now = Instant.ofEpochSecond(1234567890L);
        long step = Totp.stepAt(now);
        String code = Totp.codeAt(RFC_KEY, step);
        OptionalLong first = Totp.verify(secret, code, now, null);
        assertThat(first).hasValue(step);
        assertThat(Totp.verify(secret, code, now, first.getAsLong())).as("the same code again").isEmpty();
        assertThat(Totp.verify(secret, Totp.codeAt(RFC_KEY, step - 1), now, step)).as("an older code").isEmpty();
        assertThat(Totp.verify(secret, Totp.codeAt(RFC_KEY, step + 1), now, step)).as("the next code").hasValue(step + 1);
    }

    @Test
    void refusesAnythingThatIsNotSixDigits() {
        String secret = Totp.base32(RFC_KEY);
        Instant now = Instant.ofEpochSecond(1234567890L);
        assertThat(Totp.verify(secret, null, now, null)).isEmpty();
        assertThat(Totp.verify(secret, "", now, null)).isEmpty();
        assertThat(Totp.verify(secret, "12345", now, null)).isEmpty();
        assertThat(Totp.verify(secret, "1234567", now, null)).isEmpty();
        assertThat(Totp.verify(secret, "00592a", now, null)).isEmpty();
    }

    @Test
    void theQrCodeHoldsAStandardOtpauthUri() {
        String uri = Totp.otpauthUri("SheOut Console", "staff@example.com", "GEZDGNBVGY3TQOJQ");
        assertThat(uri).isEqualTo("otpauth://totp/SheOut%20Console:staff%40example.com?secret=GEZDGNBVGY3TQOJQ"
                + "&issuer=SheOut%20Console&algorithm=SHA1&digits=6&period=30");
        assertThat(QrCodes.svgDataUri(uri)).startsWith("data:image/svg+xml;base64,");
    }
}
