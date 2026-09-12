package com.sheout.auth.internal.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final String KEY_PREFIX = "otp:";
    private static final String ATTEMPT_PREFIX = "otp:attempts:";
    /** Wrong guesses allowed before the code is destroyed. */
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final OtpSender otpSender;
    private final Duration ttl;
    private final String devOtpPhone;
    private final String devOtpCode;
    private final String devOtpPhone2;
    private final String devOtpCode2;
    private final String devOtpPhone3;
    private final String devOtpCode3;

    /**
     * Three independent fixed-phone/fixed-code slots. This was originally
     * "deliberately just 2, not a speculative N-number mechanism" (one
     * CUSTOMER demo number, one DRIVER demo number - accounts are
     * one-role-per-phone, so the same number can't demo both roles). That
     * held until automated QA needed a third: exercising the new-account
     * signup path requires a phone number with NO existing account, but
     * both slot 1 and slot 2 are now permanently consumed (they each have
     * a real account from actual use) - reusing either would only ever
     * exercise the returning-user path. Slot 3 exists specifically so a
     * "genuinely new" number is available on demand for that one test,
     * without disturbing the other two demo numbers. Still three explicit
     * fields, not a generalized list - the need is for a small, known,
     * fixed set of purposes, not an open-ended one.
     */
    public OtpService(StringRedisTemplate redisTemplate,
                       OtpSender otpSender,
                       @Value("${sheout.auth.otp-ttl-seconds:300}") long ttlSeconds,
                       @Value("${sheout.auth.dev-otp-phone:}") String devOtpPhone,
                       @Value("${sheout.auth.dev-otp-code:}") String devOtpCode,
                       @Value("${sheout.auth.dev-otp-phone-2:}") String devOtpPhone2,
                       @Value("${sheout.auth.dev-otp-code-2:}") String devOtpCode2,
                       @Value("${sheout.auth.dev-otp-phone-3:}") String devOtpPhone3,
                       @Value("${sheout.auth.dev-otp-code-3:}") String devOtpCode3) {
        this.redisTemplate = redisTemplate;
        this.otpSender = otpSender;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.devOtpPhone = devOtpPhone;
        this.devOtpCode = devOtpCode;
        this.devOtpPhone2 = devOtpPhone2;
        this.devOtpCode2 = devOtpCode2;
        this.devOtpPhone3 = devOtpPhone3;
        this.devOtpCode3 = devOtpCode3;
    }

    /**
     * Generates a new code, stores it (replacing any previous one for this
     * phone number), and hands it to {@link OtpSender}. Returns false only
     * if delivery itself failed - the code is still stored either way,
     * since a delivery-layer false negative (provider says failed but the
     * SMS actually arrives) shouldn't lock the user out of retrying.
     * <p>
     * Exception: if this phone number matches one of the three
     * sheout.auth.dev-otp-phone[-N] slots (all unset/blank by default),
     * the corresponding fixed code is stored instead of a random one -
     * lets a live deploy be demoed, or QA-scripted, without a real SMS
     * provider or watching logs for the code.
     */
    public boolean requestCode(String phoneNumber) {
        String code = resolveDevCode(phoneNumber);
        if (code == null) code = generateCode();
        redisTemplate.opsForValue().set(key(phoneNumber), code, ttl);
        // The guess budget belongs to a code, not to a number. Without this
        // reset, someone who mistyped their last code five times would be
        // locked out of the fresh one on their first attempt. How many codes
        // can be asked for at all is bounded separately - see
        // OtpRateLimiter - so the total guesses per hour stay small.
        redisTemplate.delete(attemptKey(phoneNumber));
        try {
            otpSender.send(phoneNumber, code);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Verifies a code and, if correct, deletes it so it cannot be reused.
     * A wrong code is left in place so the user can retry - but only
     * MAX_ATTEMPTS times, after which the code is destroyed. See the
     * comment in the mismatch branch.
     */
    public VerificationOutcome verifyCode(String phoneNumber, String code) {
        String stored = redisTemplate.opsForValue().get(key(phoneNumber));
        if (stored == null) {
            return VerificationOutcome.NOT_FOUND_OR_EXPIRED;
        }
        if (!stored.equals(code)) {
            // The lockout the Javadoc above used to record as a follow-up.
            // It mattered more than it looked: a six-digit code with
            // unlimited guesses is not a secret, it is a formality. Fifteen
            // wrong codes in a row were all accepted as retries in testing,
            // and nothing stopped the sixteenth through the millionth inside
            // the code's lifetime. The code is now destroyed after
            // MAX_ATTEMPTS wrong guesses, so an attacker gets a handful of
            // tries out of a million rather than all of them.
            if (registerFailedAttempt(phoneNumber)) {
                redisTemplate.delete(key(phoneNumber));
                redisTemplate.delete(attemptKey(phoneNumber));
                log.warn("OTP invalidated after {} incorrect attempts", MAX_ATTEMPTS);
                return VerificationOutcome.NOT_FOUND_OR_EXPIRED;
            }
            return VerificationOutcome.MISMATCH;
        }
        redisTemplate.delete(key(phoneNumber));
        redisTemplate.delete(attemptKey(phoneNumber));
        return VerificationOutcome.MATCHED;
    }

    /** True once this number has used up its allowance of wrong guesses. */
    private boolean registerFailedAttempt(String phoneNumber) {
        try {
            Long attempts = redisTemplate.opsForValue().increment(attemptKey(phoneNumber));
            if (attempts != null && attempts == 1L) {
                // Outlives the code itself, so the counter cannot be reset
                // by simply waiting for it to lapse.
                redisTemplate.expire(attemptKey(phoneNumber), ttl.plusMinutes(5));
            }
            return attempts != null && attempts >= MAX_ATTEMPTS;
        } catch (RuntimeException ex) {
            // Fail closed here, unlike the request limiter: if we cannot
            // count attempts we cannot bound them, and an unbounded guess
            // budget on a login code is the worse risk.
            log.error("Could not record a failed OTP attempt, invalidating the code: {}", ex.getMessage());
            return true;
        }
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

    private String attemptKey(String phoneNumber) {
        return ATTEMPT_PREFIX + phoneNumber;
    }

    private String key(String phoneNumber) {
        return KEY_PREFIX + phoneNumber;
    }

    private String resolveDevCode(String phoneNumber) {
        if (!devOtpPhone.isBlank() && devOtpPhone.equals(phoneNumber)) return devOtpCode;
        if (!devOtpPhone2.isBlank() && devOtpPhone2.equals(phoneNumber)) return devOtpCode2;
        if (!devOtpPhone3.isBlank() && devOtpPhone3.equals(phoneNumber)) return devOtpCode3;
        return null;
    }
}
