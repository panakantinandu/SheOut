package com.sheout.sharedkernel.devmode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What makes DEV_MODE_ENABLED a single switch rather than one more thing to
 * remember: no code but DevMode may read a bypass setting. A bypass added
 * anywhere else - reading, say, ${sheout.testing.something} straight into a
 * service - would not be covered by the switch, and this test fails the build
 * the moment it is written. Put new bypasses on DevMode.
 */
class DevModeIsTheOnlyReaderTest {

    /** Property names that configure a bypass, as they would appear in @Value or getProperty. */
    private static final Pattern BYPASS_SETTING = Pattern.compile(
            "sheout\\.testing\\.|sheout\\.auth\\.dev-otp|sheout\\.auth\\.log-otp-codes|sheout\\.dev-mode\\.|DEV_OTP_|VERIFIED_(DRIVER_)?BYPASS_PHONE|LOG_OTP_CODES|DEV_MODE_ENABLED");

    @Test
    void noClassButDevModeReadsABypassSetting() throws IOException {
        Path main = Path.of("src/main/java");
        List<String> offenders;
        try (Stream<Path> files = Files.walk(main)) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.endsWith(Path.of("com/sheout/sharedkernel/devmode/DevMode.java")))
                    .filter(p -> {
                        try {
                            return Files.readAllLines(p).stream()
                                    .map(String::trim)
                                    // Comments may talk about the settings; only code may not read them.
                                    .filter(line -> !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*"))
                                    .anyMatch(line -> BYPASS_SETTING.matcher(line).find());
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(p -> main.relativize(p).toString())
                    .toList();
        }
        assertThat(offenders).as("classes reading a dev bypass setting directly instead of through DevMode").isEmpty();
    }
}
