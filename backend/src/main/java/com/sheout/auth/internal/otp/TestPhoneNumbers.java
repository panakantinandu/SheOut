package com.sheout.auth.internal.otp;

import com.sheout.sharedkernel.devmode.DevMode;
import org.springframework.stereotype.Component;

/**
 * Numbers that always sign in with the same code, for testing a deployment
 * without an SMS provider: the individually listed ones (DEV_OTP_NUMBERS
 * and the older DEV_OTP_PHONE slots) and every number under
 * DEV_OTP_TEST_PREFIX.
 * <p>
 * Configured, parsed and switched on or off in one place - DevMode - and
 * only ever active with DEV_MODE_ENABLED=true. With it off every number here
 * is an ordinary number: a random code, sent by the real SMS sender, and
 * the usual limits.
 * <p>
 * No SMS is sent to these numbers when they are active (see OtpService):
 * there is nothing to deliver that the tester does not already know, and a
 * text to a made-up number costs money and reaches whoever really owns it.
 */
@Component
public class TestPhoneNumbers {

    private final DevMode devMode;

    TestPhoneNumbers(DevMode devMode) {
        this.devMode = devMode;
    }

    /** True when this number signs in with a fixed code rather than a texted one. */
    public boolean isTestNumber(String phoneNumber) {
        return devMode.fixedOtpCodeFor(phoneNumber) != null;
    }

    /** The fixed code for this number, or null when it is an ordinary number. */
    public String codeFor(String phoneNumber) {
        return devMode.fixedOtpCodeFor(phoneNumber);
    }
}
