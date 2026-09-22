package com.sheout.dispatch.internal;

import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingAccepted;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingCancelled;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingCompleted;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingRequested;
import com.sheout.booking.BookingStarted;
import com.sheout.booking.BookingSummary;
import com.sheout.dispatch.DispatchExhausted;
import com.sheout.dispatch.DriverArriving;
import com.sheout.dispatch.DriverOffered;
import com.sheout.dispatch.internal.redis.ApproachStore;
import com.sheout.dispatch.internal.matching.MatchingStrategy;
import com.sheout.dispatch.internal.redis.DriverLocationStore;
import com.sheout.dispatch.internal.redis.OfferStore;
import com.sheout.dispatch.internal.redis.RoundState;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.geo.GeoDistance;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.DriverProfileSummary;
import com.sheout.users.OnlineStatus;
import com.sheout.users.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final DriverLocationStore locationStore;
    private final OfferStore offerStore;
    private final MatchingStrategy matchingStrategy;
    private final DriverProfileApi driverProfileApi;
    private final AuthApi authApi;
    private final BookingApi bookingApi;
    private final DomainEventPublisher eventPublisher;
    private final ApproachStore approachStore;

    private final double initialRadiusKm;
    private final double radiusExpansionFactor;
    private final int candidateCount;
    private final long offerWindowSeconds;
    private final int maxRetries;
    private final long searchTimeoutSeconds;
    private final double arrivingRadiusMetres;
    private final NearbyDriverPreview nearbyPreview;

    public DispatchService(
            DriverLocationStore locationStore,
            NearbyDriverPreview nearbyPreview,
            OfferStore offerStore,
            MatchingStrategy matchingStrategy,
            DriverProfileApi driverProfileApi,
            BookingApi bookingApi,
            AuthApi authApi,
            DomainEventPublisher eventPublisher,
            ApproachStore approachStore,
            @Value("${sheout.dispatch.initial-radius-km:3.0}") double initialRadiusKm,
            @Value("${sheout.dispatch.radius-expansion-factor:2.0}") double radiusExpansionFactor,
            @Value("${sheout.dispatch.candidate-count:5}") int candidateCount,
            @Value("${sheout.dispatch.offer-window-seconds:15}") long offerWindowSeconds,
            @Value("${sheout.dispatch.max-retries:3}") int maxRetries,
            // The TOTAL budget for one search, across every round and every
            // radius expansion. NOT the per-offer accept window above, which
            // bounds one driver answering one offer - the two solve different
            // problems and must never be collapsed into each other.
            @Value("${sheout.dispatch.search-timeout-seconds:90}") long searchTimeoutSeconds,
            // How close to the pickup counts as arriving. Close enough that
            // "look up now" is true; far enough that the rider has time to.
            @Value("${sheout.dispatch.arriving-radius-metres:300}") double arrivingRadiusMetres
    ) {
        this.locationStore = locationStore;
        this.nearbyPreview = nearbyPreview;
        this.offerStore = offerStore;
        this.matchingStrategy = matchingStrategy;
        this.driverProfileApi = driverProfileApi;
        this.authApi = authApi;
        this.bookingApi = bookingApi;
        this.eventPublisher = eventPublisher;
        this.approachStore = approachStore;
        this.arrivingRadiusMetres = arrivingRadiusMetres;
        this.initialRadiusKm = initialRadiusKm;
        this.radiusExpansionFactor = radiusExpansionFactor;
        this.candidateCount = candidateCount;
        this.offerWindowSeconds = offerWindowSeconds;
        this.maxRetries = maxRetries;
        this.searchTimeoutSeconds = searchTimeoutSeconds;
        log.info("Dispatch: up to {} retries, {}s per offer, {}s total search budget",
                maxRetries, offerWindowSeconds, searchTimeoutSeconds);
    }

    public void recordLocation(UUID driverId, double lat, double lng) {
        locationStore.recordLocation(driverId, lat, lng);
        // Arriving is read off the reports she already sends, not polled for.
        approachStore.find(driverId).ifPresent(approach -> {
            double metres = GeoDistance.haversineKm(lat, lng, approach.pickupLat(), approach.pickupLng()) * 1000;
            if (metres <= arrivingRadiusMetres && approachStore.finish(driverId)) {
                eventPublisher.publish(new DriverArriving(approach.bookingId(), approach.customerId(), driverId));
            }
        });
    }

    /**
     * She has accepted: from now until she is near the pickup, her location
     * reports are checked against it. After commit, so an accept that rolled
     * back never starts an approach.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingAccepted(BookingAccepted event) {
        bookingApi.findById(event.bookingId()).ifPresent(booking -> approachStore.start(
                event.driverId(), booking.id(), booking.customerId(), booking.pickup().lat(), booking.pickup().lng()));
    }

    /** Starting the trip, finishing it or cancelling it all end an approach that never reached "arriving". */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingStarted(BookingStarted event) {
        approachStore.finish(event.driverId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingCompleted(BookingCompleted event) {
        approachStore.finish(event.driverId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingCancelled(BookingCancelled event) {
        // Before the round is cleared: withdrawing reads who was offered it.
        offerStore.withdrawOffers(event.bookingId());
        offerStore.clearRound(event.bookingId());
        if (event.driverId() != null) {
            approachStore.find(event.driverId())
                    .filter(approach -> approach.bookingId().equals(event.bookingId()))
                    .ifPresent(approach -> approachStore.finish(event.driverId()));
        }
    }

    /** How far the "partners near you" preview looks, and how many it shows at most. */
    public static final double PREVIEW_RADIUS_KM = 3.0;
    static final int PREVIEW_MAX_SHOWN = 8;
    /** Partners share every few seconds while online; a position older than this is not "here now". */
    private static final Duration PREVIEW_FRESH_FOR = Duration.ofMinutes(2);

    /**
     * Partners who could take this ride right now, near this point, at
     * blurred positions - the riders' pre-booking "partners near you".
     * <p>
     * Same geo search and the same availability gate matching uses, so the
     * preview never shows somebody who could not actually be offered the trip:
     * online, verified, not blocked, not mid-trip, not held for an unpaid fare,
     * with the right vehicle. What comes back is a set of approximate points
     * and nothing else - see NearbyDriverPreview for how, and why the blur is
     * stable. Shuffled, so the order does not say which is closest.
     */
    public List<ApproximatePosition> previewNearby(double lat, double lng, BookingCategory category) {
        Instant freshSince = Instant.now().minus(PREVIEW_FRESH_FOR);
        List<ApproximatePosition> shown = new java.util.ArrayList<>(locationStore
                .findNearby(lat, lng, PREVIEW_RADIUS_KM, PREVIEW_MAX_SHOWN * 2)
                .stream()
                .filter(candidate -> isEligible(candidate.driverId(), category))
                .flatMap(candidate -> locationStore.findLocation(candidate.driverId())
                        .filter(location -> location.recordedAt().isAfter(freshSince))
                        .map(location -> nearbyPreview.blur(candidate.driverId(), location.lat(), location.lng()))
                        .stream())
                .limit(PREVIEW_MAX_SHOWN)
                .toList());
        java.util.Collections.shuffle(shown);
        return shown;
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
        // The deadline for the WHOLE search is fixed here, once, and copied
        // forward through every retry. Not recomputed per round, which would
        // let a search that kept finding rounds to run go on indefinitely -
        // which is precisely the hole this closes.
        Instant startedAt = Instant.now();
        RoundState firstRound = new RoundState(
                1, initialRadiusKm, event.pickup().lat(), event.pickup().lng(), event.category(),
                event.customerId(), startedAt, startedAt.plusSeconds(searchTimeoutSeconds));
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

            // Two independent limits, and whichever is hit first ends the
            // search. The time budget is checked FIRST, and it is checked
            // against when the NEXT round would finish, not just against now.
            //
            // Checking only "has the deadline passed" is not enough, and a
            // live run proved it: rounds are only examined when one expires,
            // so a round starting a second before the deadline still runs a
            // full offer window past it. With a 90s budget and a 15s window
            // that gives up at ~104s, which is not the 90s anybody
            // configured - and it fires after the client's own fallback,
            // inverting the order those two are meant to happen in.
            //
            // So a round that cannot finish inside the budget is not started
            // at all. This does NOT shorten anybody's accept window: the
            // window stays exactly as configured, and a round either runs in
            // full or does not run. The two timeouts stay separate.
            //
            // A round ending exactly ON the deadline is inside the budget and
            // does run - see noRoomForAnotherRound for what getting that
            // boundary wrong cost in testing.
            Instant now = Instant.now();
            if (current.deadlinePassed(now) || current.noRoomForAnotherRound(now, offerWindowSeconds)) {
                giveUp(bookingId, current, DispatchExhausted.Reason.SEARCH_TIMED_OUT, now);
                continue;
            }

            // attempt=1 is the initial round (not itself a retry), so maxRetries
            // retries means attempt is allowed to reach maxRetries + 1 total rounds.
            if (current.attempt() > maxRetries) {
                giveUp(bookingId, current, DispatchExhausted.Reason.RETRIES_EXHAUSTED, now);
                continue;
            }

            runRound(bookingId, current.nextAttempt(current.radiusKm() * radiusExpansionFactor));
        }
    }

    /**
     * Ends a search that found nobody, and says so out loud.
     * <p>
     * The saying-so is the whole point. This used to clear its Redis state
     * and return, which left the booking in REQUESTED with nothing running -
     * a rider watching "Searching for a nearby driver..." on a search that
     * had already stopped, with no way to tell the difference and no end to
     * it. Silence was the bug; the event is the fix.
     * <p>
     * Dispatch does not set the booking's status itself. It reports that it
     * has stopped looking, and booking decides what that means for a
     * booking - which is the module boundary this codebase keeps everywhere
     * else.
     */
    private void giveUp(UUID bookingId, RoundState state, DispatchExhausted.Reason reason, Instant now) {
        offerStore.clearRound(bookingId);
        Duration searchedFor = Duration.between(state.searchStartedAt(), now);
        log.info("Dispatch gave up on booking {} after {} rounds and {}s: {}",
                bookingId, state.attempt(), searchedFor.toSeconds(), reason);
        eventPublisher.publish(new DispatchExhausted(
                bookingId, state.customerId(), reason, state.attempt(), searchedFor));
    }

    /**
     * The total time budget for one search, in seconds, so a client can show
     * an honest countdown instead of guessing.
     * <p>
     * Exposed rather than duplicated in the apps: a frontend that hardcodes
     * its own idea of the timeout drifts the moment this is retuned, and the
     * drift is invisible until a rider is shown "no drivers" while the
     * search is still running, or left spinning after it stopped.
     */
    public long searchTimeoutSeconds() {
        return searchTimeoutSeconds;
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
                // One person can hold a rider account and a partner account
                // on the same number. Her own ride is never offered to her.
                .filter(candidate -> !authApi.samePerson(candidate.driverId(), state.customerId()))
                // Already deciding on another trip: her screen shows one offer
                // at a time - see OfferStore.createOffer.
                .filter(candidate -> offerStore.findActiveOfferForDriver(candidate.driverId()).isEmpty())
                .toList();

        List<CandidateDriver> offered = matchingStrategy.rank(eligible).stream()
                .limit(candidateCount)
                .toList();

        Duration window = Duration.ofSeconds(offerWindowSeconds);
        Instant expiresAt = Instant.now().plus(window);

        java.util.Set<UUID> actuallyOffered = new java.util.HashSet<>();
        for (CandidateDriver candidate : offered) {
            // False when another search claimed her between the filter above
            // and now. She is not marked as tried for this booking, so a later
            // round can still offer it to her once she is free.
            if (!offerStore.createOffer(bookingId, candidate.driverId(), window)) {
                continue;
            }
            actuallyOffered.add(candidate.driverId());
            // After the offer exists, so a partner alerted by this can accept it.
            eventPublisher.publish(new DriverOffered(
                    bookingId, candidate.driverId(), state.category(), candidate.distanceKm(), expiresAt));
        }
        if (!actuallyOffered.isEmpty()) {
            offerStore.markTried(bookingId, actuallyOffered);
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
     * <p>
     * A blocked account is refused here too, and here only - the same
     * single place, for the same reason. Blocking is meant to take effect
     * immediately regardless of what the driver's online flag or
     * verification says, and a blocked driver who happens to be ONLINE and
     * verified would otherwise keep receiving real bookings. Adding a
     * second check somewhere else would recreate exactly the drift this
     * method exists to prevent.
     */
    private boolean isAvailableNow(UUID driverId) {
        Optional<DriverProfileSummary> profile = driverProfileApi.findByAccountId(driverId);
        if (profile.isEmpty() || profile.get().onlineStatus() != OnlineStatus.ONLINE) {
            return false;
        }
        if (authApi.findAccount(driverId).map(AccountSummary::blocked).orElse(true)) {
            return false;
        }
        // A trip she has just ended is still waiting for the rider's
        // payment. She is not sent to someone else until it lands - or until
        // the hold runs out, so a rider who never pays cannot keep her off
        // the road. Checked here, the one availability gate, so it covers
        // both new offers and accepting one already on her screen.
        if (bookingApi.findPaymentHoldForDriver(driverId).isPresent()) {
            return false;
        }
        // Already on a trip. Nothing checked this: a partner with a rider
        // aboard stayed in the geo set and could be offered, and could
        // accept, a second trip mid-ride.
        if (bookingApi.hasActiveTripAsDriver(driverId)) {
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
