package com.sheout.sharedkernel.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Fixed-window counter in Redis, so a limit holds across instances rather
 * than per process.
 * <p>
 * The increment and the expiry are one Lua script, not two calls. The
 * two-call version - INCR, then EXPIRE if the count is 1 - leaves a key with
 * no expiry if the process dies or Redis blips between them, and that key is
 * a permanent lockout for whoever it names. The script also repairs a key it
 * finds with no TTL, for the same reason.
 * <p>
 * Fixed windows allow up to twice the limit across a window boundary. That
 * is accepted: every limit here is a bound on abuse, not an exact quota, and
 * a sliding window would cost a sorted set per subject for no difference an
 * attacker could exploit meaningfully.
 * <p>
 * Fails OPEN: if Redis cannot be reached the attempt is allowed. Each limit
 * sits in front of something with its own guard (OTP attempt counts, the
 * pickup code's per-trip lockout), and Redis being down is already breaking
 * OTP storage and dispatch. Locking every user out on top of that is the
 * worse outcome.
 */
@Component
class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String PREFIX = "rl:";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count > 0 then
              return redis.call('DECR', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RateLimitDecision tryConsume(String key, int limit, Duration window) {
        try {
            List<?> result = redis.execute(SCRIPT, List.of(PREFIX + key), String.valueOf(window.toMillis()));
            long count = ((Number) result.get(0)).longValue();
            long ttlMillis = ((Number) result.get(1)).longValue();
            if (count <= limit) {
                return RateLimitDecision.allow();
            }
            // Round up: "retry in 0s" after 400ms would invite an immediate
            // retry that is refused again.
            return RateLimitDecision.refuse((ttlMillis + 999) / 1000);
        } catch (RuntimeException ex) {
            // The key is not logged: several of them name a phone number.
            log.error("Rate limiter unavailable, allowing the request: {}", ex.getClass().getSimpleName());
            return RateLimitDecision.allow();
        }
    }

    @Override
    public void release(String key) {
        try {
            redis.execute(RELEASE, List.of(PREFIX + key));
        } catch (RuntimeException ex) {
            // Failing to give an attempt back only makes the limit stricter.
            log.warn("Rate limiter unavailable, attempt not released: {}", ex.getClass().getSimpleName());
        }
    }
}
