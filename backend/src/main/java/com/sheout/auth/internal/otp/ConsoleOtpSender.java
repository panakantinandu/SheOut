package com.sheout.auth.internal.otp;

import com.sheout.sharedkernel.logging.Redact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dev/default OTP "delivery" - just logs the code. Active until a real
 * provider (MSG91, Twilio, ...) is wired in behind {@link OtpSender}; see
 * that interface's Javadoc for why Firebase isn't a drop-in fit here.
 * <p>
 * The code itself is only logged when sheout.auth.log-otp-codes is on, which
 * application-local.yml does and production does not. It used to be logged
 * unconditionally, beside the full phone number, at INFO - so anybody who
 * could read the production logs could sign in as anybody who had just
 * asked for a code. The number is always masked.
 * <p>
 * With it off and no SMS provider configured, a code reaches nobody. Sign-in
 * on a deployed environment then works only for the DEV_OTP_PHONE demo
 * slots, whose codes are fixed. Turning LOG_OTP_CODES on in production
 * restores log-reading sign-in, and with it the exposure above.
 */
@Component
public class ConsoleOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleOtpSender.class);

    private final boolean logCodes;

    ConsoleOtpSender(@Value("${sheout.auth.log-otp-codes:false}") boolean logCodes) {
        this.logCodes = logCodes;
    }

    @Override
    public void send(String phoneNumber, String code) {
        if (logCodes) {
            log.info("[dev OTP] {} -> {}", Redact.phone(phoneNumber), code);
        } else {
            log.info("[dev OTP] code generated for {} (not delivered: no SMS provider; code logging is off)",
                    Redact.phone(phoneNumber));
        }
    }
}
