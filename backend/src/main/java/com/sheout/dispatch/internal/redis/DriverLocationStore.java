package com.sheout.dispatch.internal.redis;

import com.sheout.dispatch.internal.CandidateDriver;
import com.sheout.dispatch.internal.DriverLocation;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The ONLY class in this module that touches Redis GEO commands - every
 * other class works with {@link CandidateDriver}, never a raw Point/
 * GeoResults type. Kept this narrow deliberately: this is the highest
 * compile-risk file in this pass (GEOADD/GEOSEARCH via Spring Data Redis's
 * modern geo API, not the older, more commonly-seen geoRadius() one - see
 * the module README note flagging that this specific file could not be
 * verified against a real build). If the exact class/method names here
 * turn out to be wrong, the fix is contained to this one file.
 * <p>
 * Point/GeoReference coordinate order is (longitude, latitude), NOT
 * (latitude, longitude) - a classic Redis GEO footgun, called out
 * explicitly at every call site below.
 */
@Component
public class DriverLocationStore {

    private static final String GEO_KEY = "dispatch:driver-geo";
    private static final String TS_KEY_PREFIX = "dispatch:driver-loc-ts:";

    /**
     * Long enough to outlive any sane polling gap, short enough that a
     * driver who stopped reporting eventually stops looking live.
     */
    private static final Duration TS_TTL = Duration.ofHours(6);

    private final StringRedisTemplate redisTemplate;

    public DriverLocationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordLocation(UUID driverId, double lat, double lng) {
        GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();
        // Point(x, y) = Point(longitude, latitude).
        geoOps.add(GEO_KEY, new Point(lng, lat), driverId.toString());
        // Redis GEO stores a position and nothing else, so freshness is kept
        // in its own key - see DriverLocation.recordedAt for why that matters.
        redisTemplate.opsForValue().set(TS_KEY_PREFIX + driverId, Long.toString(System.currentTimeMillis()), TS_TTL);
    }

    public void remove(UUID driverId) {
        redisTemplate.opsForGeo().remove(GEO_KEY, driverId.toString());
        redisTemplate.delete(TS_KEY_PREFIX + driverId);
    }

    /**
     * This driver's last known position, or empty if they have never
     * reported one (or were removed on going offline). GEOPOS reads the
     * stored point for one member, rather than searching an area the way
     * findNearby does.
     */
    public Optional<DriverLocation> findLocation(UUID driverId) {
        List<Point> points = redisTemplate.opsForGeo().position(GEO_KEY, driverId.toString());
        if (points == null || points.isEmpty() || points.get(0) == null) {
            return Optional.empty();
        }
        Point point = points.get(0);
        String timestamp = redisTemplate.opsForValue().get(TS_KEY_PREFIX + driverId);
        // EPOCH marks "position exists but predates timestamp tracking" - callers
        // render that as unknown freshness rather than as 1970.
        Instant recordedAt = timestamp == null ? Instant.EPOCH : Instant.ofEpochMilli(Long.parseLong(timestamp));
        // Point(x, y) = Point(longitude, latitude) - unwound back to (lat, lng) here.
        return Optional.of(new DriverLocation(point.getY(), point.getX(), recordedAt));
    }

    /**
     * Nearest drivers within radiusKm, closest first, up to limit. Callers
     * apply their own eligibility filtering (online status, vehicle type,
     * already-tried exclusion) on the result - this method only knows
     * about geography.
     */
    public List<CandidateDriver> findNearby(double lat, double lng, double radiusKm, int limit) {
        GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();

        GeoShape searchArea = GeoShape.byRadius(new Distance(radiusKm, Metrics.KILOMETERS));
        GeoReference<String> reference = GeoReference.fromCoordinate(lng, lat);
        GeoSearchCommandArgs args = GeoSearchCommandArgs.newGeoSearchArgs()
                .includeDistance()
                .sortAscending()
                .limit(limit);

        var results = geoOps.search(GEO_KEY, reference, searchArea, args);
        if (results == null) {
            return List.of();
        }

        return results.getContent().stream()
                .map(result -> {
                    GeoLocation<String> location = result.getContent();
                    return new CandidateDriver(UUID.fromString(location.getName()), result.getDistance().getValue());
                })
                .toList();
    }

    /** Convenience overload for excluding driverIds already offered this booking in an earlier round. */
    public List<CandidateDriver> findNearby(double lat, double lng, double radiusKm, int limit, Set<UUID> exclude) {
        // Over-fetch, then filter, since GEOSEARCH has no "exclude these members" option.
        return findNearby(lat, lng, radiusKm, limit + exclude.size())
                .stream()
                .filter(candidate -> !exclude.contains(candidate.driverId()))
                .limit(limit)
                .toList();
    }
}
