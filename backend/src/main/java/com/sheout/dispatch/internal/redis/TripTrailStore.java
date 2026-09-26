package com.sheout.dispatch.internal.redis;

import com.sheout.dispatch.DriverLocation;
import com.sheout.dispatch.TripTrailApi;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Each live trip's trail, in Redis: a list of "lat,lng,epochMillis" per
 * booking, and a marker saying which booking a partner is currently driving.
 * <p>
 * The trail outlives the trip by a day - long enough for completion to read
 * it and for anyone debugging a flag the same day, and no longer: a woman's
 * path through the city is not something to keep by default.
 */
@Component
public class TripTrailStore implements TripTrailApi {

    private static final String ACTIVE_PREFIX = "dispatch:trail-active:";
    private static final String TRAIL_PREFIX = "dispatch:trail:";
    /** Past any real trip; a trail left behind by a trip that never ended goes too. */
    private static final Duration ACTIVE_TTL = Duration.ofHours(6);
    private static final Duration TRAIL_TTL = Duration.ofHours(24);
    /** ~10 hours of reports every 7 s. Bounds a runaway list, not a real trip. */
    private static final int MAX_POINTS = 5000;

    private final StringRedisTemplate redis;

    public TripTrailStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** She has started this trip; her reports from now on are its trail, beginning where she is. */
    public void start(UUID driverId, UUID bookingId, DriverLocation startingAt) {
        String trail = TRAIL_PREFIX + bookingId;
        redis.delete(trail);
        redis.opsForValue().set(ACTIVE_PREFIX + driverId, bookingId.toString(), ACTIVE_TTL);
        if (startingAt != null) {
            push(trail, startingAt.lat(), startingAt.lng(), startingAt.recordedAt());
        }
    }

    /** One location report. Recorded only while she has a trip under way. */
    public void append(UUID driverId, double lat, double lng, Instant at) {
        String bookingId = redis.opsForValue().get(ACTIVE_PREFIX + driverId);
        if (bookingId != null) {
            push(TRAIL_PREFIX + bookingId, lat, lng, at);
        }
    }

    /** The trip is over, one way or another. The trail itself stays until it expires. */
    public void stop(UUID driverId) {
        redis.delete(ACTIVE_PREFIX + driverId);
    }

    @Override
    public List<DriverLocation> trail(UUID bookingId) {
        List<String> raw = redis.opsForList().range(TRAIL_PREFIX + bookingId, 0, -1);
        List<DriverLocation> points = new ArrayList<>();
        if (raw == null) {
            return points;
        }
        for (String entry : raw) {
            String[] parts = entry.split(",");
            if (parts.length != 3) {
                continue;
            }
            try {
                points.add(new DriverLocation(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                        Instant.ofEpochMilli(Long.parseLong(parts[2]))));
            } catch (NumberFormatException ignored) {
                // A malformed entry is skipped, not allowed to spoil the rest.
            }
        }
        return points;
    }

    private void push(String key, double lat, double lng, Instant at) {
        redis.opsForList().rightPush(key, String.format(Locale.ROOT, "%.6f,%.6f,%d", lat, lng, at.toEpochMilli()));
        redis.opsForList().trim(key, -MAX_POINTS, -1);
        redis.expire(key, TRAIL_TTL);
    }
}
