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
    private final String devOtpPhone2;
    private final String devOtpCode2;

    /**
     * Two independent fixed-phone/fixed-code slots, not a general list -
     * this is deliberately just enough for "one CUSTOMER demo number, one
     * DRIVER demo number" (accounts are one-role-per-phone, so the same
     * number can't demo both roles), not a speculative N-number mechanism.
     */
    public OtpService(StringRedisTemplate redisTemplate,
                       OtpSender otpSender,
                       @Value("${sheout.auth.otp-ttl-seconds:300}") long ttlSeconds,
                       @Value("${sheout.auth.dev-otp-phone:}") String devOtpPhone,
                       @Value("${sheout.auth.dev-otp-code:}") String devOtpCode,
                       @Value("${sheout.auth.dev-otp-phone-2:}") String devOtpPhone2,
                       @Value("${sheout.auth.dev-otp-code-2:}") String devOtpCode2) {
        this.redisTemplate = redisTemplate;
        this.otpSender = otpSender;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.devOtpPhone = devOtpPhone;
        this.devOtpCode = devOtpCode;
        this.devOtpPhone2 = devOtpPhone2;
        this.devOtpCode2 = devOtpCode2;
    }

    /**
     * Generates a new code, stores it (replacing any previous one for this
     * phone number), and hands it to {@link OtpSender}. Returns false only
     * if delivery itself failed - the code is still stored either way,
     * since a delivery-layer false negative (provider says failed but the
     * SMS actually arrives) shouldn't lock the user out of retrying.
     * <p>
     * Exception: if this phone number matches sheout.auth.dev-otp-phone or
     * dev-otp-phone-2 (both unset/blank by default), the corresponding
     * fixed code is stored instead of a random one - lets a live deploy be
     * demoed without a real SMS provider or watching logs for the code.
     */
    public boolean requestCode(String phoneNumber) {
        String code = resolveDevCode(phoneNumber);
        if (code == null) code = generateCode();
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

    private String resolveDevCode(String phoneNumber) {
        if (!devOtpPhone.isBlank() && devOtpPhone.equals(phoneNumber)) return devOtpCode;
        if (!devOtpPhone2.isBlank() && devOtpPhone2.equals(phoneNumber)) return devOtpCode2;
        return null;
    }
}
