package com.sheout.auth.internal.otp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Generates, stores (in Redis, with a TTL - no DB round trip for something
 * this ephemeral), and verifies OTP codes. Delivery is delegated entirely
 * to {@link OtpSender}.
 */
@Component
public class OtpService {

    private static final String KEY_PREFIX = "otp:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final OtpSender otpSender;
    private final Duration ttl;
    private final String devOtpPhone;
    private final String devOtpCode;

    public OtpService(StringRedisTemplate redisTemplate,
                       OtpSender otpSender,
                       @Value("${sheout.auth.otp-ttl-seconds:300}") long ttlSeconds,
                       @Value("${sheout.auth.dev-otp-phone:}") String devOtpPhone,
                       @Value("${sheout.auth.dev-otp-code:}") String devOtpCode) {
        this.redisTemplate = redisTemplate;
        this.otpSender = otpSender;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.devOtpPhone = devOtpPhone;
        this.devOtpCode = devOtpCode;
    }

    /**
     * Generates a new code, stores it (replacing any previous one for this
     * phone number), and hands it to {@link OtpSender}. Returns false only
     * if delivery itself failed - the code is still stored either way,
     * since a delivery-layer false negative (provider says failed but the
     * SMS actually arrives) shouldn't lock the user out of retrying.
     * <p>
     * Exception: if this phone number matches sheout.auth.dev-otp-phone
     * (unset/blank by default), the fixed dev-otp-code is stored instead of
     * a random one - lets a live deploy be demoed without a real SMS
     * provider or watching logs for the code.
     */
    public boolean requestCode(String phoneNumber) {
        String code = isDevOtpPhone(phoneNumber) ? devOtpCode : generateCode();
        redisTemplate.opsForValue().set(key(phoneNumber), code, ttl);
        try {
            otpSender.send(phoneNumber, code);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Verifies a code and, if correct, deletes it so it can't be reused.
     * An incorrect code is left in place so the user can retry until it
     * expires - no attempt-count lockout is implemented (not specified;
     * flagged as a follow-up in the README).
     */
    public VerificationOutcome verifyCode(String phoneNumber, String code) {
        String stored = redisTemplate.opsForValue().get(key(phoneNumber));
        if (stored == null) {
            return VerificationOutcome.NOT_FOUND_OR_EXPIRED;
        }
        if (!stored.equals(code)) {
            return VerificationOutcome.MISMATCH;
        }
        redisTemplate.delete(key(phoneNumber));
        return VerificationOutcome.MATCHED;
    }

    public enum VerificationOutcome {
        MATCHED,
        NOT_FOUND_OR_EXPIRED,
        MISMATCH
    }

    private String generateCode() {
        int code = RANDOM.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    private String key(String phoneNumber) {
        return KEY_PREFIX + phoneNumber;
    }

    private boolean isDevOtpPhone(String phoneNumber) {
        return !devOtpPhone.isBlank() && devOtpPhone.equals(phoneNumber);
    }
}
