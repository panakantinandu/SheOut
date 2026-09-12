package com.sheout.auth.internal.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Caps how often a code can be requested for one phone number.
 * <p>
 * Without this, the OTP endpoint was an SMS cannon. Twelve requests for one
 * number landed in half a second in testing, each one an SMS attempt. That
 * is two separate problems at once: it bills us per message on a paid
 * Twilio account, and it lets anyone use SheOut to repeatedly text a number
 * they do not own. The victim is not even a user.
 * <p>
 * Two limits, because one is not enough. A short cooldown stops the rapid
 * burst; an hourly cap stops a patient attacker pacing themselves just
 * outside it. Both keyed on the number being texted, since that is who
 * suffers - an IP cap alone protects the bill and not the person.
 * <p>
 * Redis-backed, so the limit holds across instances rather than per-process.
 * Deliberately fails OPEN: if Redis is unreachable the request is allowed
 * through. Someone unable to receive a login code is a worse outcome than
 * an unmetered SMS, and Redis being down is already breaking OTP storage
 * itself a moment later.
 */
@Component
public class OtpRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(OtpRateLimiter.class);
    private static final String COOLDOWN_PREFIX = "otp:cooldown:";
    private static final String HOURLY_PREFIX = "otp:hourly:";

    private final StringRedisTemplate redis;
    private final Duration cooldown;
    private final int hourlyLimit;

    OtpRateLimiter(StringRedisTemplate redis,
                   @Value("${sheout.auth.otp-cooldown-seconds:45}") long cooldownSeconds,
                   @Value("${sheout.auth.otp-hourly-limit:6}") int hourlyLimit) {
        this.redis = redis;
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
        this.hourlyLimit = hourlyLimit;
    }

    /** True when a code may be sent to this number right now. */
    public boolean allow(String phoneNumber) {
        try {
            Boolean firstInWindow = redis.opsForValue()
                    .setIfAbsent(COOLDOWN_PREFIX + phoneNumber, "1", cooldown);
            if (!Boolean.TRUE.equals(firstInWindow)) {
                log.info("OTP request refused - still inside the {}s cooldown for this number", cooldown.toSeconds());
                return false;
            }

            String hourlyKey = HOURLY_PREFIX + phoneNumber;
            Long used = redis.opsForValue().increment(hourlyKey);
            if (used != null && used == 1L) {
                redis.expire(hourlyKey, Duration.ofHours(1));
            }
            if (used != null && used > hourlyLimit) {
                log.warn("OTP request refused - {} requests for one number within the hour", used);
                return false;
            }
            return true;
        } catch (RuntimeException ex) {
            // See the class comment: fail open rather than lock people out.
            log.error("OTP rate limiter unavailable, allowing the request: {}", ex.getMessage());
            return true;
        }
    }
}
