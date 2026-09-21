package com.sheout.auth.internal.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Numbers that always get the same code, for testing a live deploy.
 * <p>
 * There were three of these, one per @Value slot, added one at a time as
 * testing needed another - a rider, a partner, then a spare for signing up
 * fresh. Three is not enough to test two riders in one trip, or a partner
 * and a rider and an admin at once, and adding a fourth meant editing Java
 * and redeploying. So it is a list in one environment variable now:
 * <pre>
 *   DEV_OTP_NUMBERS=+919000000001:123456,+919000000002:654321
 * </pre>
 * Separated by commas or whitespace, each entry a number and its code. The
 * three old slots still work and are folded in, so nothing already
 * configured has to change.
 * <p>
 * UNSET BY DEFAULT, and meant to be unset again once real SMS is live: a
 * number listed here signs in with a code that never changes. It is here so
 * that testing a real deployment does not depend on an SMS provider, not as
 * a permanent fixture.
 * <p>
 * No SMS is sent to these numbers even when a provider is configured (see
 * OtpService) - there is nothing to deliver that the tester does not
 * already know, and a text to a made-up number costs money and reaches
 * whoever really owns it.
 */
@Component
public class TestPhoneNumbers {

    private static final Logger log = LoggerFactory.getLogger(TestPhoneNumbers.class);

    private final Map<String, String> codes = new LinkedHashMap<>();

    TestPhoneNumbers(@Value("${sheout.auth.dev-otp-numbers:}") String list,
                     @Value("${sheout.auth.dev-otp-phone:}") String phone1,
                     @Value("${sheout.auth.dev-otp-code:}") String code1,
                     @Value("${sheout.auth.dev-otp-phone-2:}") String phone2,
                     @Value("${sheout.auth.dev-otp-code-2:}") String code2,
                     @Value("${sheout.auth.dev-otp-phone-3:}") String phone3,
                     @Value("${sheout.auth.dev-otp-code-3:}") String code3) {
        add(phone1, code1);
        add(phone2, code2);
        add(phone3, code3);
        for (String entry : list.split("[,\\s]+")) {
            if (entry.isBlank()) {
                continue;
            }
            int separator = entry.lastIndexOf(':');
            if (separator < 0) {
                // Named, not silent: a typo here means somebody's test sign-in
                // fails for a reason nothing else would explain.
                log.warn("Ignoring test phone entry without a code (expected +91...:123456)");
                continue;
            }
            add(entry.substring(0, separator), entry.substring(separator + 1));
        }
        if (!codes.isEmpty()) {
            log.info("Test sign-in numbers configured: {}. Unset DEV_OTP_NUMBERS once real SMS is live.", codes.size());
        }
    }

    private void add(String phoneNumber, String code) {
        String number = phoneNumber == null ? "" : phoneNumber.trim();
        String fixed = code == null ? "" : code.trim();
        if (number.isBlank() || fixed.isBlank()) {
            return;
        }
        if (!fixed.matches("\\d{6}")) {
            log.warn("Ignoring test phone number whose code is not six digits");
            return;
        }
        codes.put(number, fixed);
    }

    /** True when this number signs in with a fixed code rather than a texted one. */
    public boolean isTestNumber(String phoneNumber) {
        return codes.containsKey(phoneNumber);
    }

    /** The fixed code for this number, or null when it is an ordinary number. */
    public String codeFor(String phoneNumber) {
        return codes.get(phoneNumber);
    }
}
