package com.sheout.dispatch.internal.redis;

import com.sheout.booking.BookingCategory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Every other piece of dispatch's ephemeral offer/race/retry state, kept
 * separate from {@link DriverLocationStore} (geo only) for clarity. Uses
 * only the well-established value/hash/set/zset Redis APIs - the risk in
 * this module is concentrated in DriverLocationStore, not here.
 */
@Component
public class OfferStore {

    private static final String OFFER_KEY_PREFIX = "dispatch:offer:";
    private static final String DRIVER_ACTIVE_OFFER_PREFIX = "dispatch:driver-active-offer:";
    private static final String CLAIM_KEY_PREFIX = "dispatch:booking-claim:";
    private static final String ROUND_KEY_PREFIX = "dispatch:round:";
    private static final String TRIED_KEY_SUFFIX = ":tried";
    private static final String PENDING_ROUNDS_KEY = "dispatch:pending-rounds";
    private static final Duration CLAIM_TTL = Duration.ofMinutes(10);
    private static final Duration ROUND_STATE_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public OfferStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Opens a pending offer for one driver on one booking, expiring after the accept window. */
    public void createOffer(UUID bookingId, UUID driverId, Duration window) {
        redisTemplate.opsForValue().set(offerKey(bookingId, driverId), "1", window);
        redisTemplate.opsForValue().set(driverActiveOfferKey(driverId), bookingId.toString(), window);
    }

    /** What a driver polls to discover a pending offer. */
    public Optional<UUID> findActiveOfferForDriver(UUID driverId) {
        String value = redisTemplate.opsForValue().get(driverActiveOfferKey(driverId));
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    public boolean hasOffer(UUID bookingId, UUID driverId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(offerKey(bookingId, driverId)));
    }

    public void removeOffer(UUID bookingId, UUID driverId) {
        redisTemplate.delete(offerKey(bookingId, driverId));
        redisTemplate.delete(driverActiveOfferKey(driverId));
    }

    /**
     * Atomic race resolution: true if this call is the one that won (first
     * driver to accept), false if another driver already claimed this
     * booking. Backed by SET ... NX, so this is safe under concurrent calls.
     */
    public boolean tryClaim(UUID bookingId, UUID driverId) {
        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(claimKey(bookingId), driverId.toString(), CLAIM_TTL);
        return Boolean.TRUE.equals(claimed);
    }

    public boolean isClaimed(UUID bookingId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(claimKey(bookingId)));
    }

    /** Releases a claim if BookingApi.assignDriver ends up failing after we won the race - lets a retry happen instead of wedging the booking. */
    public void releaseClaim(UUID bookingId) {
        redisTemplate.delete(claimKey(bookingId));
    }

    public void recordRound(UUID bookingId, RoundState state, Instant expiresAt) {
        String key = roundKey(bookingId);
        redisTemplate.opsForHash().put(key, "attempt", String.valueOf(state.attempt()));
        redisTemplate.opsForHash().put(key, "radiusKm", String.valueOf(state.radiusKm()));
        redisTemplate.opsForHash().put(key, "pickupLat", String.valueOf(state.pickupLat()));
        redisTemplate.opsForHash().put(key, "pickupLng", String.valueOf(state.pickupLng()));
        redisTemplate.opsForHash().put(key, "category", state.category().name());
        redisTemplate.expire(key, ROUND_STATE_TTL);
        redisTemplate.opsForZSet().add(PENDING_ROUNDS_KEY, bookingId.toString(), expiresAt.toEpochMilli());
    }

    public Optional<RoundState> getRound(UUID bookingId) {
        String key = roundKey(bookingId);
        Object attempt = redisTemplate.opsForHash().get(key, "attempt");
        Object radiusKm = redisTemplate.opsForHash().get(key, "radiusKm");
        Object pickupLat = redisTemplate.opsForHash().get(key, "pickupLat");
        Object pickupLng = redisTemplate.opsForHash().get(key, "pickupLng");
        Object category = redisTemplate.opsForHash().get(key, "category");
        if (attempt == null || radiusKm == null || pickupLat == null || pickupLng == null || category == null) {
            return Optional.empty();
        }
        return Optional.of(new RoundState(
                Integer.parseInt(attempt.toString()),
                Double.parseDouble(radiusKm.toString()),
                Double.parseDouble(pickupLat.toString()),
                Double.parseDouble(pickupLng.toString()),
                BookingCategory.valueOf(category.toString())
        ));
    }

    public void markTried(UUID bookingId, Set<UUID> driverIds) {
        if (driverIds.isEmpty()) {
            return;
        }
        String[] ids = driverIds.stream().map(UUID::toString).toArray(String[]::new);
        redisTemplate.opsForSet().add(triedKey(bookingId), ids);
        redisTemplate.expire(triedKey(bookingId), ROUND_STATE_TTL);
    }

    public Set<UUID> getTried(UUID bookingId) {
        Set<String> members = redisTemplate.opsForSet().members(triedKey(bookingId));
        if (members == null) {
            return Set.of();
        }
        return members.stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    /** Booking ids whose current round's accept window has passed, for the retry sweeper. Removes them from the pending set as it returns them. */
    public Set<UUID> pollExpiredRounds(Instant now) {
        Set<String> expired = redisTemplate.opsForZSet()
                .rangeByScore(PENDING_ROUNDS_KEY, 0, now.toEpochMilli());
        if (expired == null || expired.isEmpty()) {
            return new HashSet<>();
        }
        expired.forEach(id -> redisTemplate.opsForZSet().remove(PENDING_ROUNDS_KEY, id));
        return expired.stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    public void clearRound(UUID bookingId) {
        redisTemplate.delete(roundKey(bookingId));
        redisTemplate.delete(triedKey(bookingId));
        redisTemplate.opsForZSet().remove(PENDING_ROUNDS_KEY, bookingId.toString());
    }

    private String offerKey(UUID bookingId, UUID driverId) {
        return OFFER_KEY_PREFIX + bookingId + ":" + driverId;
    }

    private String driverActiveOfferKey(UUID driverId) {
        return DRIVER_ACTIVE_OFFER_PREFIX + driverId;
    }

    private String claimKey(UUID bookingId) {
        return CLAIM_KEY_PREFIX + bookingId;
    }

    private String roundKey(UUID bookingId) {
        return ROUND_KEY_PREFIX + bookingId;
    }

    private String triedKey(UUID bookingId) {
        return ROUND_KEY_PREFIX + bookingId + TRIED_KEY_SUFFIX;
    }
}
