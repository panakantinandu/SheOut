package com.sheout.sharedkernel.devmode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every dev/test bypass in the application, behind one switch.
 * <p>
 * THE ONLY READER. This is the only class allowed to read a bypass setting
 * (sheout.auth.dev-otp-*, sheout.auth.log-otp-codes, sheout.testing.*) -
 * DevModeIsTheOnlyReaderTest fails the build if anything else does. Everything
 * that bypasses a real check asks this class, and this class answers "none"
 * for all of them unless DEV_MODE_ENABLED is true. So one flag, off by
 * default, guarantees every bypass is off - whatever else happens to be set.
 * <p>
 * The bypasses:
 * <ul>
 *   <li>fixed-code sign-in numbers (DEV_OTP_NUMBERS and the three older
 *       DEV_OTP_PHONE slots) - no text is sent, the code never changes, and
 *       the per-number request limits do not apply;</li>
 *   <li>the fixed-code prefix (DEV_OTP_TEST_PREFIX + DEV_OTP_TEST_CODE) -
 *       the same, for every number starting with the prefix;</li>
 *   <li>writing sign-in codes into the log (LOG_OTP_CODES);</li>
 *   <li>the rider and partner verification bypasses (VERIFIED_BYPASS_PHONE,
 *       VERIFIED_DRIVER_BYPASS_PHONE).</li>
 * </ul>
 * ADMIN_BOOTSTRAP_PHONE is not here on purpose: it is not a bypass but how
 * an operator account comes to exist, and production needs it.
 */
@Component
public class DevMode {

    private static final Logger log = LoggerFactory.getLogger(DevMode.class);

    /**
     * The shortest prefix accepted: a country code and at least six digits.
     * "+91" alone would make every Indian number a test number; this refuses
     * anything that broad rather than trusting it was meant.
     */
    static final int MIN_PREFIX_DIGITS = 8;

    private final boolean enabled;
    private final Map<String, String> fixedCodeNumbers;
    private final String testPrefix;
    private final String testPrefixCode;
    private final boolean logOtpCodes;
    private final String verifiedRiderPhone;
    private final String verifiedPartnerPhone;

    public DevMode(@Value("${sheout.dev-mode.enabled:false}") boolean enabled,
                   @Value("${sheout.auth.dev-otp-numbers:}") String numberList,
                   @Value("${sheout.auth.dev-otp-phone:}") String phone1,
                   @Value("${sheout.auth.dev-otp-code:}") String code1,
                   @Value("${sheout.auth.dev-otp-phone-2:}") String phone2,
                   @Value("${sheout.auth.dev-otp-code-2:}") String code2,
                   @Value("${sheout.auth.dev-otp-phone-3:}") String phone3,
                   @Value("${sheout.auth.dev-otp-code-3:}") String code3,
                   @Value("${sheout.auth.dev-otp-test-prefix:}") String testPrefix,
                   @Value("${sheout.auth.dev-otp-test-code:123456}") String testPrefixCode,
                   @Value("${sheout.auth.log-otp-codes:false}") boolean logOtpCodes,
                   @Value("${sheout.testing.verified-bypass-phone:}") String verifiedRiderPhone,
                   @Value("${sheout.testing.verified-driver-bypass-phone:}") String verifiedPartnerPhone) {
        this.enabled = enabled;

        Map<String, String> numbers = new LinkedHashMap<>();
        addNumber(numbers, phone1, code1);
        addNumber(numbers, phone2, code2);
        addNumber(numbers, phone3, code3);
        for (String entry : numberList.split("[,\\s]+")) {
            if (entry.isBlank()) {
                continue;
            }
            int separator = entry.lastIndexOf(':');
            if (separator < 0) {
                log.warn("Ignoring test phone entry without a code (expected +91...:123456)");
                continue;
            }
            addNumber(numbers, entry.substring(0, separator), entry.substring(separator + 1));
        }
        String prefix = validPrefix(testPrefix);
        String prefixCode = isSixDigits(testPrefixCode) ? testPrefixCode.trim() : null;

        List<String> configured = new ArrayList<>();
        if (!numbers.isEmpty()) configured.add(numbers.size() + " fixed-code number(s)");
        if (prefix != null && prefixCode != null) configured.add("fixed-code prefix " + mask(prefix));
        if (logOtpCodes) configured.add("sign-in codes written to the log");
        if (!verifiedRiderPhone.isBlank()) configured.add("rider verification bypass");
        if (!verifiedPartnerPhone.isBlank()) configured.add("partner verification bypass");

        if (enabled) {
            this.fixedCodeNumbers = Collections.unmodifiableMap(numbers);
            this.testPrefix = prefixCode == null ? null : prefix;
            this.testPrefixCode = prefixCode;
            this.logOtpCodes = logOtpCodes;
            this.verifiedRiderPhone = verifiedRiderPhone.trim();
            this.verifiedPartnerPhone = verifiedPartnerPhone.trim();
            log.warn("DEV MODE IS ON. Active bypasses: {}. Set DEV_MODE_ENABLED=false before real users.",
                    configured.isEmpty() ? "none configured" : String.join(", ", configured));
        } else {
            this.fixedCodeNumbers = Map.of();
            this.testPrefix = null;
            this.testPrefixCode = null;
            this.logOtpCodes = false;
            this.verifiedRiderPhone = "";
            this.verifiedPartnerPhone = "";
            if (!configured.isEmpty()) {
                // Said out loud: somebody reading the settings would otherwise
                // believe these were live.
                log.info("Dev mode is off: ignoring {}.", String.join(", ", configured));
            }
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * The fixed code for a test number, or null for a real one. A number
     * listed individually wins over the prefix. Always null with dev mode off.
     */
    public String fixedOtpCodeFor(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String listed = fixedCodeNumbers.get(phoneNumber);
        if (listed != null) {
            return listed;
        }
        if (testPrefix != null && phoneNumber.startsWith(testPrefix)
                && phoneNumber.length() > testPrefix.length()
                && phoneNumber.substring(testPrefix.length()).chars().allMatch(Character::isDigit)) {
            return testPrefixCode;
        }
        return null;
    }

    /** Whether sign-in codes may be written to the log. False with dev mode off. */
    public boolean logOtpCodes() {
        return logOtpCodes;
    }

    /** The one rider number allowed to book unverified, if any. Empty with dev mode off. */
    public Optional<String> verifiedRiderBypassPhone() {
        return verifiedRiderPhone.isBlank() ? Optional.empty() : Optional.of(verifiedRiderPhone);
    }

    /** The one partner number allowed to go online unverified, if any. Empty with dev mode off. */
    public Optional<String> verifiedPartnerBypassPhone() {
        return verifiedPartnerPhone.isBlank() ? Optional.empty() : Optional.of(verifiedPartnerPhone);
    }

    private static void addNumber(Map<String, String> numbers, String phoneNumber, String code) {
        String number = phoneNumber == null ? "" : phoneNumber.trim();
        if (number.isBlank() || code == null || code.isBlank()) {
            return;
        }
        if (!isSixDigits(code)) {
            log.warn("Ignoring test phone number whose code is not six digits");
            return;
        }
        numbers.put(number, code.trim());
    }

    /** "+" and at least MIN_PREFIX_DIGITS digits, nothing else; otherwise refused (null) and said so. */
    static String validPrefix(String raw) {
        String prefix = raw == null ? "" : raw.trim();
        if (prefix.isEmpty()) {
            return null;
        }
        if (!prefix.matches("\\+\\d{" + MIN_PREFIX_DIGITS + ",14}")) {
            log.error("Ignoring DEV_OTP_TEST_PREFIX: it must be + and at least {} digits (e.g. +91999999), "
                    + "or it could match real people's numbers", MIN_PREFIX_DIGITS);
            return null;
        }
        return prefix;
    }

    private static boolean isSixDigits(String code) {
        return code != null && code.trim().matches("\\d{6}");
    }

    private static String mask(String prefix) {
        return prefix.substring(0, Math.min(4, prefix.length())) + "…";
    }
}
