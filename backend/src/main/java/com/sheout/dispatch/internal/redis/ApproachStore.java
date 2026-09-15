package com.sheout.dispatch.internal.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A partner on her way to a pickup: which booking, whose, and where the
 * pickup is. One entry per partner, written when she accepts and removed the
 * moment she is arriving or the trip starts, ends or is cancelled.
 * <p>
 * It exists so a location report - several a minute per partner - can check
 * "is she near her pickup" against Redis, without a database query for every
 * position.
 */
@Component
public class ApproachStore {

    private static final String KEY_PREFIX = "dispatch:approach:";

    /** Past any real approach; stops an entry for a trip nothing ever closed from lingering. */
    private static final Duration TTL = Duration.ofHours(3);

    private final StringRedisTemplate redisTemplate;

    public ApproachStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void start(UUID driverId, UUID bookingId, UUID customerId, double pickupLat, double pickupLng) {
        String key = KEY_PREFIX + driverId;
        redisTemplate.opsForHash().putAll(key, Map.of(
                "bookingId", bookingId.toString(),
                "customerId", customerId.toString(),
                "lat", Double.toString(pickupLat),
                "lng", Double.toString(pickupLng)));
        redisTemplate.expire(key, TTL);
    }

    public Optional<Approach> find(UUID driverId) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(KEY_PREFIX + driverId);
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Approach(
                    UUID.fromString((String) fields.get("bookingId")),
                    UUID.fromString((String) fields.get("customerId")),
                    Double.parseDouble((String) fields.get("lat")),
                    Double.parseDouble((String) fields.get("lng"))));
        } catch (RuntimeException malformed) {
            redisTemplate.delete(KEY_PREFIX + driverId);
            return Optional.empty();
        }
    }

    /**
     * Ends the approach. True only for the one caller that actually removed
     * it: two location reports landing together both see her inside the
     * radius, and only one of them may announce it.
     */
    public boolean finish(UUID driverId) {
        return Boolean.TRUE.equals(redisTemplate.delete(KEY_PREFIX + driverId));
    }

    public record Approach(UUID bookingId, UUID customerId, double pickupLat, double pickupLng) {
    }
}
