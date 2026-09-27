package com.sheout.booking.internal;

import com.sheout.booking.BookingError;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.DestinationChangeDeclined;
import com.sheout.booking.DestinationChangeRequested;
import com.sheout.booking.DestinationChanged;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.internal.fare.FareCalculator;
import com.sheout.booking.internal.fare.FareQuote;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.geo.GeoDistance;
import com.sheout.sharedkernel.geo.ServiceArea;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * A rider changing where she is going, mid-trip, with her partner's say-so.
 * <p>
 * ASKED, THEN AGREED. The rider asks; nothing about the trip changes until
 * the partner accepts. A different drop is a different job - a longer ride,
 * a later finish, possibly somewhere she would rather not end up at night -
 * so it is hers to take or leave, the same way a trip offer is. If she
 * declines, or does not answer within the window, the trip goes on to the
 * drop it was booked to at the fare it was booked at.
 * <p>
 * PRICED THE WAY EVERY TRIP IS. The new fare is the same FareCalculator's
 * quote the booking was created with, from the same basis: the pickup to the
 * (new) drop over real roads. Nothing here works a fare out on its own, and
 * the number the rider is shown before she asks - from the ordinary quote
 * endpoint - is the number her partner is shown and the one charged.
 * <p>
 * ONCE PER TRIP. After her partner has answered one request - yes or no - she
 * cannot ask again on this trip: a ride is not a negotiation. A request that
 * ran out unanswered does not count, because nobody said no to it. She can
 * always get off early with "End trip here".
 * <p>
 * THE ROUTE CHECK FOLLOWS THE AGREEMENT. Accepting replaces the booking's
 * quoted distance with the new route's, so the check at the end compares the
 * road she drove with the trip both of them agreed to - never the one they
 * agreed to leave. See BookingEntity.changeDestination and RouteCheck.
 */
@Service
public class DestinationChangeService {

    /** A new drop closer than this to the current one is the same place. Matches the drop-off radius. */
    static final double SAME_PLACE_METRES = 150;

    private static final EnumSet<DestinationChangeEntity.Status> ANSWERED =
            EnumSet.of(DestinationChangeEntity.Status.ACCEPTED, DestinationChangeEntity.Status.DECLINED);

    private final BookingRepository bookings;
    private final DestinationChangeRepository changes;
    private final FareCalculator fareCalculator;
    private final ServiceArea serviceArea;
    private final DomainEventPublisher events;
    private final Duration answerWindow;
    private final int maxAnsweredPerTrip;
    private final Clock clock;

    @Autowired
    public DestinationChangeService(BookingRepository bookings, DestinationChangeRepository changes,
                                    FareCalculator fareCalculator, ServiceArea serviceArea, DomainEventPublisher events,
                                    // Long enough for a partner on a bike to pull over and
                                    // read it; short enough that the rider is not left
                                    // wondering while the road to the old drop runs out.
                                    @Value("${sheout.booking.destination-change.answer-seconds:120}") long answerSeconds,
                                    @Value("${sheout.booking.destination-change.max-per-trip:1}") int maxAnsweredPerTrip) {
        this(bookings, changes, fareCalculator, serviceArea, events, Duration.ofSeconds(answerSeconds),
                maxAnsweredPerTrip, Clock.systemUTC());
    }

    DestinationChangeService(BookingRepository bookings, DestinationChangeRepository changes,
                             FareCalculator fareCalculator, ServiceArea serviceArea, DomainEventPublisher events,
                             Duration answerWindow, int maxAnsweredPerTrip, Clock clock) {
        this.bookings = bookings;
        this.changes = changes;
        this.fareCalculator = fareCalculator;
        this.serviceArea = serviceArea;
        this.events = events;
        this.answerWindow = answerWindow;
        this.maxAnsweredPerTrip = maxAnsweredPerTrip;
        this.clock = clock;
    }

    /** One request as either side sees it. secondsLeft is worked out here so a phone's wrong clock cannot shorten it. */
    public record DestinationChangeView(
            UUID id,
            UUID bookingId,
            DestinationChangeEntity.Status status,
            GeoAddress oldDrop,
            GeoAddress newDrop,
            BigDecimal oldFare,
            BigDecimal newFare,
            BigDecimal oldDistanceKm,
            BigDecimal newDistanceKm,
            boolean newDistanceRouted,
            Instant requestedAt,
            Instant expiresAt,
            Instant answeredAt,
            long secondsLeft
    ) {
    }

    /** The latest request on a trip, if any, and whether the rider may make one now. */
    public record DestinationChangeState(DestinationChangeView change, boolean canRequest) {
    }

    /**
     * The rider asks. expectedFare is the fare she was shown; when the trip
     * no longer prices at that - a night rate starting in between, say - she
     * is refused and shown the new figure rather than sent a request carrying
     * a number she never saw.
     */
    @Transactional
    public Result<DestinationChangeView, BookingError> request(UUID bookingId, UUID customerId, GeoAddress newDrop,
                                                              BigDecimal expectedFare) {
        Optional<BookingEntity> found = bookings.findLockedById(bookingId);
        if (found.isEmpty() || !found.get().getCustomerId().equals(customerId)) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();
        if (booking.getStatus() != BookingStatus.IN_PROGRESS || booking.getDriverId() == null) {
            return Result.failure(BookingError.INVALID_STATE_TRANSITION);
        }
        if (!serviceArea.covers(newDrop.lat(), newDrop.lng())) {
            return Result.failure(BookingError.OUTSIDE_SERVICE_AREA);
        }

        Instant now = clock.instant();
        for (DestinationChangeEntity pending : changes.findByBookingIdAndStatus(bookingId, DestinationChangeEntity.Status.PENDING)) {
            if (pending.effectiveStatus(now) == DestinationChangeEntity.Status.PENDING) {
                return Result.failure(BookingError.DESTINATION_CHANGE_PENDING);
            }
            // Ran out unanswered. Written down now, and flushed, so the
            // one-pending-per-trip index does not refuse the new row.
            pending.lapse(now);
            changes.saveAndFlush(pending);
        }
        if (changes.countByBookingIdAndStatusIn(bookingId, ANSWERED) >= maxAnsweredPerTrip) {
            return Result.failure(BookingError.DESTINATION_CHANGE_LIMIT_REACHED);
        }
        GeoAddressEmbeddable current = booking.getDrop();
        if (GeoDistance.haversineKm(current.getLat(), current.getLng(), newDrop.lat(), newDrop.lng()) * 1000 < SAME_PLACE_METRES) {
            return Result.failure(BookingError.DESTINATION_UNCHANGED);
        }

        FareQuote quote = fareCalculator.quote(booking.getCategory(), booking.getPickup().toGeoAddress(), newDrop);
        if (expectedFare != null && expectedFare.compareTo(quote.amount()) != 0) {
            return Result.failure(BookingError.DESTINATION_FARE_CHANGED);
        }

        DestinationChangeEntity change = new DestinationChangeEntity(
                bookingId, customerId, GeoAddressEmbeddable.from(current.toGeoAddress()), GeoAddressEmbeddable.from(newDrop),
                booking.getFareEstimate(), quote.amount(),
                booking.getQuotedDistanceKm(),
                BigDecimal.valueOf(quote.distanceKm()).setScale(2, RoundingMode.HALF_UP), quote.routed(),
                now.plus(answerWindow));
        changes.save(change);

        events.publish(new DestinationChangeRequested(change.getId(), bookingId, customerId, booking.getDriverId(),
                newDrop, change.getOldFare(), change.getNewFare(), change.getExpiresAt()));
        return Result.success(toView(change, booking, now));
    }

    /**
     * Her partner answers. Yes changes the trip's drop, fare and quoted
     * distance together, in this transaction; no changes nothing. Either way
     * the rider is told - see the events.
     */
    @Transactional
    public Result<DestinationChangeView, BookingError> answer(UUID bookingId, UUID driverId, boolean accept) {
        Optional<BookingEntity> found = bookings.findLockedById(bookingId);
        if (found.isEmpty() || !driverId.equals(found.get().getDriverId())) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();
        Instant now = clock.instant();
        Optional<DestinationChangeEntity> latest = changes.findFirstByBookingIdOrderByCreatedAtDesc(bookingId);
        if (latest.isEmpty() || latest.get().getStatus() != DestinationChangeEntity.Status.PENDING) {
            return Result.failure(BookingError.DESTINATION_CHANGE_NOT_PENDING);
        }
        DestinationChangeEntity change = latest.get();
        if (change.effectiveStatus(now) != DestinationChangeEntity.Status.PENDING
                || booking.getStatus() != BookingStatus.IN_PROGRESS) {
            change.lapse(now);
            changes.save(change);
            return Result.failure(BookingError.DESTINATION_CHANGE_NOT_PENDING);
        }

        GeoAddress oldDrop = booking.getDrop().toGeoAddress();
        if (accept) {
            change.answer(DestinationChangeEntity.Status.ACCEPTED, now);
            booking.changeDestination(GeoAddressEmbeddable.from(change.getNewDrop().toGeoAddress()), change.getNewFare(), change.getNewDistanceKm(),
                    change.isNewDistanceRouted(), now);
            bookings.save(booking);
            changes.save(change);
            events.publish(new DestinationChanged(change.getId(), bookingId, booking.getCustomerId(), driverId,
                    oldDrop, change.getNewDrop().toGeoAddress(), change.getOldFare(), change.getNewFare()));
        } else {
            change.answer(DestinationChangeEntity.Status.DECLINED, now);
            changes.save(change);
            events.publish(new DestinationChangeDeclined(change.getId(), bookingId, booking.getCustomerId(), driverId,
                    oldDrop, booking.getFareEstimate()));
        }
        return Result.success(toView(change, booking, now));
    }

    /** For either participant; the controller has checked which. */
    public Optional<DestinationChangeState> state(UUID bookingId) {
        return bookings.findById(bookingId).map(booking -> {
            Instant now = clock.instant();
            Optional<DestinationChangeEntity> latest = changes.findFirstByBookingIdOrderByCreatedAtDesc(bookingId);
            DestinationChangeView view = latest.map(c -> toView(c, booking, now)).orElse(null);
            boolean waiting = view != null && view.status() == DestinationChangeEntity.Status.PENDING;
            boolean canRequest = booking.getStatus() == BookingStatus.IN_PROGRESS
                    && !waiting
                    && changes.countByBookingIdAndStatusIn(bookingId, ANSWERED) < maxAnsweredPerTrip;
            return new DestinationChangeState(view, canRequest);
        });
    }

    private static DestinationChangeView toView(DestinationChangeEntity c, BookingEntity booking, Instant now) {
        DestinationChangeEntity.Status status = c.effectiveStatus(now);
        // A question about a trip that has already ended has no answer left to give.
        if (status == DestinationChangeEntity.Status.PENDING && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            status = DestinationChangeEntity.Status.EXPIRED;
        }
        long secondsLeft = status == DestinationChangeEntity.Status.PENDING
                ? Math.max(0, Duration.between(now, c.getExpiresAt()).toSeconds())
                : 0;
        return new DestinationChangeView(c.getId(), c.getBookingId(), status,
                c.getOldDrop().toGeoAddress(), c.getNewDrop().toGeoAddress(),
                c.getOldFare(), c.getNewFare(), c.getOldDistanceKm(), c.getNewDistanceKm(), c.isNewDistanceRouted(),
                c.getCreatedAt(), c.getExpiresAt(), c.getAnsweredAt(), secondsLeft);
    }
}
