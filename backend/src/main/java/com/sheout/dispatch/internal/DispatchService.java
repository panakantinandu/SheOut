package com.sheout.dispatch.internal;

import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingRequested;
import com.sheout.booking.BookingSummary;
import com.sheout.dispatch.internal.matching.MatchingStrategy;
import com.sheout.dispatch.internal.redis.DriverLocationStore;
import com.sheout.dispatch.internal.redis.OfferStore;
import com.sheout.dispatch.internal.redis.RoundState;
import com.sheout.sharedkernel.Result;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.DriverProfileSummary;
import com.sheout.users.OnlineStatus;
import com.sheout.users.VehicleType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The core matching workflow. Depends on BookingApi and DriverProfileApi
 * only (per the instruction to keep this module's cross-module surface
 * minimal, since it may need to become its own service before others do) -
 * never touches booking's or users' persistence directly.
 */
@Service
public class DispatchService {

    private final DriverLocationStore locationStore;
    private final OfferStore offerStore;
    private final MatchingStrategy matchingStrategy;
    private final DriverProfileApi driverProfileApi;
    private final BookingApi bookingApi;

    private final double initialRadiusKm;
    private final double radiusExpansionFactor;
    private final int candidateCount;
    private final long offerWindowSeconds;
    private final int maxRetries;

    public DispatchService(
            DriverLocationStore locationStore,
            OfferStore offerStore,
            MatchingStrategy matchingStrategy,
            DriverProfileApi driverProfileApi,
            BookingApi bookingApi,
            @Value("${sheout.dispatch.initial-radius-km:3.0}") double initialRadiusKm,
            @Value("${sheout.dispatch.radius-expansion-factor:2.0}") double radiusExpansionFactor,
            @Value("${sheout.dispatch.candidate-count:5}") int candidateCount,
            @Value("${sheout.dispatch.offer-window-seconds:15}") long offerWindowSeconds,
            @Value("${sheout.dispatch.max-retries:3}") int maxRetries
    ) {
        this.locationStore = locationStore;
        this.offerStore = offerStore;
        this.matchingStrategy = matchingStrategy;
        this.driverProfileApi = driverProfileApi;
        this.bookingApi = bookingApi;
        this.initialRadiusKm = initialRadiusKm;
        this.radiusExpansionFactor = radiusExpansionFactor;
        this.candidateCount = candidateCount;
        this.offerWindowSeconds = offerWindowSeconds;
        this.maxRetries = maxRetries;
    }

    public void recordLocation(UUID driverId, double lat, double lng) {
        locationStore.recordLocation(driverId, lat, lng);
    }

    /** Last reported position for a driver, or empty if they have never reported one. */
    public Optional<DriverLocation> findDriverLocation(UUID driverId) {
        return locationStore.findLocation(driverId);
    }

    /**
     * Reacts to a new booking rather than booking calling into dispatch
     * directly - booking has no idea dispatch exists, matching this
     * codebase's event-for-cross-module-reactions convention.
     */
    @EventListener
    public void onBookingRequested(BookingRequested event) {
        RoundState firstRound = new RoundState(
                1, initialRadiusKm, event.pickup().lat(), event.pickup().lng(), event.category());
        runRound(event.bookingId(), firstRound);
    }

    public Optional<UUID> findActiveOffer(UUID driverId) {
        return offerStore.findActiveOfferForDriver(driverId);
    }

    /**
     * The accept flow, including the explicitly-required race check: a
     * driver's eligibility is re-verified here, live, not trusted from
     * whatever it was at candidate-selection time. That claim used to be
     * only half true - it re-read online status but not verification, so a
     * driver could be offered a booking and still accept it after their
     * verification was revoked. Both are re-read now, via isAvailableNow.
     */
    public Result<Void, DispatchError> acceptOffer(UUID bookingId, UUID driverId) {
        if (!offerStore.hasOffer(bookingId, driverId)) {
            return Result.failure(DispatchError.OFFER_NOT_FOUND);
        }
        if (!isAvailableNow(driverId)) {
            offerStore.removeOffer(bookingId, driverId);
            return Result.failure(DispatchError.DRIVER_NO_LONGER_ELIGIBLE);
        }
        if (!offerStore.tryClaim(bookingId, driverId)) {
            return Result.failure(DispatchError.BOOKING_ALREADY_ASSIGNED);
        }

        Result<BookingSummary, BookingError> assigned = bookingApi.assignDriver(bookingId, driverId);
        if (assigned.isFailure()) {
            // Someone/something else changed the booking's state between our claim and this call
            // (e.g. the customer cancelled) - release the claim rather than wedging the booking forever.
            offerStore.releaseClaim(bookingId);
            return Result.failure(DispatchError.ASSIGNMENT_FAILED);
        }

        offerStore.clearRound(bookingId);
        offerStore.removeOffer(bookingId, driverId);
        return Result.success(null);
    }

    public void declineOffer(UUID bookingId, UUID driverId) {
        offerStore.removeOffer(bookingId, driverId);
        offerStore.markTried(bookingId, Set.of(driverId));
    }

    /** Called by DispatchRetrySweeper - see that class for why this is a periodic sweep rather than a one-off scheduled task. */
    public void sweepExpiredRounds() {
        Set<UUID> expiredBookingIds = offerStore.pollExpiredRounds(Instant.now());
        for (UUID bookingId : expiredBookingIds) {
            if (offerStore.isClaimed(bookingId)) {
                offerStore.clearRound(bookingId);
                continue;
            }

            Optional<RoundState> round = offerStore.getRound(bookingId);
            if (round.isEmpty()) {
                continue;
            }
            RoundState current = round.get();

            // attempt=1 is the initial round (not itself a retry), so maxRetries
            // retries means attempt is allowed to reach maxRetries + 1 total rounds.
            if (current.attempt() > maxRetries) {
                // Retries exhausted - leave the booking REQUESTED for manual/customer-visible retry, per spec.
                offerStore.clearRound(bookingId);
                continue;
            }

            RoundState next = new RoundState(
                    current.attempt() + 1,
                    current.radiusKm() * radiusExpansionFactor,
                    current.pickupLat(),
                    current.pickupLng(),
                    current.category()
            );
            runRound(bookingId, next);
        }
    }

    /**
     * One round: find nearby candidates (excluding drivers already tried
     * in an earlier round for this booking - the "next-ring" half of the
     * spec's "expanded radius or next-ring drivers" retry strategy, combined
     * here with radius expansion rather than treated as an alternative to it),
     * filter to eligible ones, rank, and broadcast an offer to the top N.
     */
    private void runRound(UUID bookingId, RoundState state) {
        Set<UUID> alreadyTried = offerStore.getTried(bookingId);

        // Over-fetch since eligibility filtering (online + vehicle type) happens after the geo query.
        List<CandidateDriver> nearby = locationStore.findNearby(
                state.pickupLat(), state.pickupLng(), state.radiusKm(), candidateCount * 3, alreadyTried);

        List<CandidateDriver> eligible = nearby.stream()
                .filter(candidate -> isEligible(candidate.driverId(), state.category()))
                .toList();

        List<CandidateDriver> offered = matchingStrategy.rank(eligible).stream()
                .limit(candidateCount)
                .toList();

        Duration window = Duration.ofSeconds(offerWindowSeconds);
        Instant expiresAt = Instant.now().plus(window);

        if (!offered.isEmpty()) {
            for (CandidateDriver candidate : offered) {
                offerStore.createOffer(bookingId, candidate.driverId(), window);
            }
            offerStore.markTried(bookingId, offered.stream().map(CandidateDriver::driverId).collect(Collectors.toSet()));
        }
        // Recorded even with zero candidates this round, so the sweeper still picks this booking
        // back up and expands the radius further next time, rather than it going silently stuck.
        offerStore.recordRound(bookingId, state, expiresAt);
    }

    /**
     * The one place that answers "may this driver be given work right now?"
     * - both the candidate filter and the accept race check go through it,
     * so the two cannot drift apart again.
     * <p>
     * ONLINE alone is not enough, and this is the bug that made it matter:
     * going ONLINE is gated on verification, but that gate runs once, at
     * the moment of the toggle. A driver who was never really verified
     * (the testing bypass lets one account toggle itself online), or whose
     * verification is later revoked or reset, kept an ONLINE flag that
     * nothing re-examined - and kept being offered real bookings. So
     * verification is re-read live here, on every offer and every accept,
     * exactly as DriverProfileService re-reads it on the toggle.
     * <p>
     * Deliberately NOT DriverProfileSummary.verified: that field is a
     * cached projection of an AccountVerified event and can be stale,
     * which would reintroduce the same class of bug one layer down.
     */
    private boolean isAvailableNow(UUID driverId) {
        Optional<DriverProfileSummary> profile = driverProfileApi.findByAccountId(driverId);
        if (profile.isEmpty() || profile.get().onlineStatus() != OnlineStatus.ONLINE) {
            return false;
        }
        return driverProfileApi.isCurrentlyVerified(driverId);
    }

    private boolean isEligible(UUID driverId, BookingCategory category) {
        if (!isAvailableNow(driverId)) {
            return false;
        }
        return driverProfileApi.findByAccountId(driverId)
                .map(profile -> vehicleMatches(profile.vehicleType(), category))
                .orElse(false);
    }

    /**
     * ASSUMPTION FLAGGED: vehicle-type/category matching isn't in the
     * explicit requirements for this pass (only "nearest N... ONLINE" is
     * named), but offering a CAB booking to a BIKE driver seemed like a
     * correctness gap rather than a "smarter scoring" enhancement, so it's
     * included as a basic eligibility filter, separate from MatchingStrategy's
     * ranking. PARCEL/LUNCHBOX are matched to BIKE drivers specifically -
     * a guess (two-wheeler delivery is realistic for Hyderabad), not
     * something users' VehicleType or booking's BookingCategory says
     * anywhere.
     */
    private boolean vehicleMatches(VehicleType vehicleType, BookingCategory category) {
        return switch (category) {
            case BIKE -> vehicleType == VehicleType.BIKE;
            case AUTO -> vehicleType == VehicleType.AUTO;
            case CAB -> vehicleType == VehicleType.CAB;
            case PARCEL, LUNCHBOX -> vehicleType == VehicleType.BIKE;
        };
    }
}
