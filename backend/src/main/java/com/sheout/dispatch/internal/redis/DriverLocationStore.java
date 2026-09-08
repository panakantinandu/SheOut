package com.sheout.dispatch.internal.redis;

import com.sheout.dispatch.internal.CandidateDriver;
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

import java.util.List;
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

    private final StringRedisTemplate redisTemplate;

    public DriverLocationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordLocation(UUID driverId, double lat, double lng) {
        GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();
        // Point(x, y) = Point(longitude, latitude).
        geoOps.add(GEO_KEY, new Point(lng, lat), driverId.toString());
    }

    public void remove(UUID driverId) {
        redisTemplate.opsForGeo().remove(GEO_KEY, driverId.toString());
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
