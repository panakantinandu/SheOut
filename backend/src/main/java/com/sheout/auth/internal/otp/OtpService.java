package com.sheout.auth.internal.otp;

import com.sheout.auth.AccountRole;
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
    private final TestPhoneNumbers testNumbers;

    /**
     * The fixed-code numbers now live in one place - see TestPhoneNumbers,
     * which also explains why there used to be exactly three of them and
     * why that stopped being enough.
     */
    public OtpService(StringRedisTemplate redisTemplate,
                       OtpSender otpSender,
                       TestPhoneNumbers testNumbers,
                       @Value("${sheout.auth.otp-ttl-seconds:300}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.otpSender = otpSender;
        this.testNumbers = testNumbers;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Generates a new code, stores it (replacing any previous one for this
     * phone number), and hands it to {@link OtpSender}. Returns false only
     * if delivery itself failed - the code is still stored either way,
     * since a delivery-layer false negative (provider says failed but the
     * SMS actually arrives) shouldn't lock the user out of retrying.
     * <p>
     * Exception: a number configured as a test number (see
     * TestPhoneNumbers, unset by default) gets its fixed code stored
     * instead of a random one, and nothing is sent - there is nothing to
     * deliver that the tester does not already know, and a text to a
     * made-up number costs money and reaches whoever really owns it.
     */
    /**
     * Codes are kept per number AND per app. With one key per number, asking
     * for a code in the partner app silently replaced the one just sent to
     * the rider app, and the rider's correct code then read as "incorrect".
     */
    public boolean requestCode(String phoneNumber, AccountRole role) {
        String fixed = testNumbers.codeFor(phoneNumber);
        String code = fixed != null ? fixed : generateCode();
        redisTemplate.opsForValue().set(key(phoneNumber, role), code, ttl);
        // The guess budget belongs to a code, not to a number. Without this
        // reset, someone who mistyped their last code five times would be
        // locked out of the fresh one on their first attempt. How many codes
        // can be asked for at all is bounded separately - see
        // OtpRateLimiter - so the total guesses per hour stay small.
        redisTemplate.delete(attemptKey(phoneNumber, role));
        if (fixed != null) {
            // Nothing to send: the code is in the deployment's own
            // configuration, and whoever owns this number in real life did
            // not ask us to text them.
            return true;
        }
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
    public VerificationOutcome verifyCode(String phoneNumber, AccountRole role, String code) {
        String stored = redisTemplate.opsForValue().get(key(phoneNumber, role));
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
            if (registerFailedAttempt(phoneNumber, role)) {
                redisTemplate.delete(key(phoneNumber, role));
                redisTemplate.delete(attemptKey(phoneNumber, role));
                log.warn("OTP invalidated after {} incorrect attempts", MAX_ATTEMPTS);
                return VerificationOutcome.NOT_FOUND_OR_EXPIRED;
            }
            return VerificationOutcome.MISMATCH;
        }
        redisTemplate.delete(key(phoneNumber, role));
        redisTemplate.delete(attemptKey(phoneNumber, role));
        return VerificationOutcome.MATCHED;
    }

    /** True once this number has used up its allowance of wrong guesses. */
    private boolean registerFailedAttempt(String phoneNumber, AccountRole role) {
        try {
            Long attempts = redisTemplate.opsForValue().increment(attemptKey(phoneNumber, role));
            if (attempts != null && attempts == 1L) {
                // Outlives the code itself, so the counter cannot be reset
                // by simply waiting for it to lapse.
                redisTemplate.expire(attemptKey(phoneNumber, role), ttl.plusMinutes(5));
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

    private String attemptKey(String phoneNumber, AccountRole role) {
        return ATTEMPT_PREFIX + role + ":" + phoneNumber;
    }

    private String key(String phoneNumber, AccountRole role) {
        return KEY_PREFIX + role + ":" + phoneNumber;
    }

}
