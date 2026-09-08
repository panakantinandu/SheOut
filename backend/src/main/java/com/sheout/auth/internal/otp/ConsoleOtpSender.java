package com.sheout.auth.internal.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dev/default OTP "delivery" - just logs the code. Active until a real
 * provider (MSG91, Twilio, ...) is wired in behind {@link OtpSender}; see
 * that interface's Javadoc for why Firebase isn't a drop-in fit here.
 */
@Component
public class ConsoleOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleOtpSender.class);

    @Override
    public void send(String phoneNumber, String code) {
        log.info("[dev OTP] {} -> {}", phoneNumber, code);
    }
}
