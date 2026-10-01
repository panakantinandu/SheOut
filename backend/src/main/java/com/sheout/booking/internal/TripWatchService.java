package com.sheout.booking.internal;

import com.sheout.booking.BookingStatus;
import com.sheout.booking.TripAlertRaised;
import com.sheout.booking.TripAlertsApi;
import com.sheout.dispatch.DriverLocation;
import com.sheout.dispatch.DriverLocationApi;
import com.sheout.dispatch.TripTrailApi;
import com.sheout.sharedkernel.cluster.ClusterLock;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Trip watch: every minute, every live trip is looked at for what a person
 * should know about - see TripWatchRules for the conditions. A new condition
 * raises an alert (operators are pushed, and for a long stop or a trip far
 * over time the rider is asked whether she is all right); a condition that
 * ends clears its alert by itself. Nothing here ever cancels or changes a
 * trip: the decision is always a person's.
 * <p>
 * Thresholds are configuration (sheout.trip-watch.*), so operations can make
 * it quieter or louder without a release.
 */
@Service
public class TripWatchService implements TripAlertsApi {

    private static final Logger log = LoggerFactory.getLogger(TripWatchService.class);
    private static final Set<BookingStatus> WATCHED = EnumSet.of(BookingStatus.ACCEPTED, BookingStatus.IN_PROGRESS);

    private final BookingRepository bookings;
    private final TripAlertRepository alerts;
    private final DriverLocationApi locations;
    private final TripTrailApi trails;
    private final DomainEventPublisher events;
    private final ClusterLock lock;
    private final TransactionTemplate tx;
    private final TripWatchRules.Thresholds thresholds;
    private final boolean enabled;
    /** After a person marks an alert checked, the same condition on the same trip stays quiet this long. */
    private final Duration snoozeAfterCheck;

    public TripWatchService(BookingRepository bookings, TripAlertRepository alerts, DriverLocationApi locations,
                            TripTrailApi trails, DomainEventPublisher events, ClusterLock lock,
                            PlatformTransactionManager transactions,
                            @Value("${sheout.trip-watch.enabled:true}") boolean enabled,
                            @Value("${sheout.trip-watch.stuck-accepted-minutes:45}") long stuckAcceptedMinutes,
                            @Value("${sheout.trip-watch.location-silent-minutes:3}") long silentMinutes,
                            @Value("${sheout.trip-watch.stopped-minutes:8}") long stoppedMinutes,
                            @Value("${sheout.trip-watch.stopped-within-metres:80}") double stoppedWithinMetres,
                            @Value("${sheout.trip-watch.overdue-factor:1.5}") double overdueFactor,
                            @Value("${sheout.trip-watch.overdue-slack-minutes:15}") long overdueSlackMinutes,
                            @Value("${sheout.trip-watch.snooze-after-check-minutes:30}") long snoozeMinutes) {
        this.snoozeAfterCheck = Duration.ofMinutes(snoozeMinutes);
        this.bookings = bookings;
        this.alerts = alerts;
        this.locations = locations;
        this.trails = trails;
        this.events = events;
        this.lock = lock;
        this.tx = new TransactionTemplate(transactions);
        this.enabled = enabled;
        this.thresholds = new TripWatchRules.Thresholds(Duration.ofMinutes(stuckAcceptedMinutes),
                Duration.ofMinutes(silentMinutes), Duration.ofMinutes(stoppedMinutes), stoppedWithinMetres, 250,
                overdueFactor, Duration.ofMinutes(overdueSlackMinutes), 18);
    }

    @Scheduled(initialDelayString = "${sheout.trip-watch.initial-delay-ms:60000}",
            fixedDelayString = "${sheout.trip-watch.interval-ms:60000}")
    public void sweep() {
        if (enabled) {
            lock.runExclusively("trip-watch", Duration.ofMinutes(5), this::runOnce);
        }
    }

    /** One pass over every live trip. Package-visible for tests. */
    void runOnce() {
        Instant now = Instant.now();
        List<BookingEntity> live = bookings.findByStatusIn(WATCHED);
        Map<UUID, Map<Kind, TripAlertEntity>> open = new HashMap<>();
        for (TripAlertEntity a : alerts.findByResolvedAtIsNullOrderByRaisedAtAsc()) {
            open.computeIfAbsent(a.getBookingId(), k -> new HashMap<>()).put(a.getKind(), a);
        }
        Set<UUID> seen = new HashSet<>();
        for (BookingEntity trip : live) {
            seen.add(trip.getId());
            try {
                Map<Kind, String> now_ = TripWatchRules.evaluate(factsFor(trip), now, thresholds);
                tx.executeWithoutResult(s -> reconcile(trip, now_, open.getOrDefault(trip.getId(), Map.of()), now));
            } catch (RuntimeException ex) {
                // One odd trip must not stop the others being watched.
                log.error("Trip watch could not check booking {}: {}", trip.getId(), ex.toString());
            }
        }
        // Trips that have ended since: their alerts are over.
        open.forEach((bookingId, byKind) -> {
            if (!seen.contains(bookingId)) {
                tx.executeWithoutResult(s -> byKind.values().forEach(a -> {
                    a.resolve(now, null, "Trip ended");
                    alerts.save(a);
                }));
            }
        });
    }

    private void reconcile(BookingEntity trip, Map<Kind, String> conditions, Map<Kind, TripAlertEntity> open, Instant now) {
        conditions.forEach((kind, detail) -> {
            TripAlertEntity existing = open.get(kind);
            if (existing != null) {
                existing.updateDetail(detail);
                alerts.save(existing);
                return;
            }
            // Somebody checked on this a little while ago: let her be until
            // the snooze runs out, then remind her if it is still so.
            boolean snoozed = alerts.findFirstByBookingIdAndKindAndResolvedByIsNotNullOrderByResolvedAtDesc(trip.getId(), kind)
                    .map(TripAlertEntity::getResolvedAt)
                    .filter(at -> at.isAfter(now.minus(snoozeAfterCheck)))
                    .isPresent();
            if (snoozed) {
                return;
            }
            TripAlertEntity raised = alerts.save(new TripAlertEntity(trip.getId(), kind, detail, now));
            log.warn("Trip watch: {} on booking {} - {}", kind, trip.getId(), detail);
            events.publish(new TripAlertRaised(raised.getId(), trip.getId(), trip.getCustomerId(), kind, detail));
        });
        open.forEach((kind, alert) -> {
            if (!conditions.containsKey(kind)) {
                alert.resolve(now, null, "Cleared by itself");
                alerts.save(alert);
            }
        });
    }

    private TripWatchRules.TripFacts factsFor(BookingEntity trip) {
        UUID partner = trip.getDriverId();
        DriverLocation fix = partner == null ? null : locations.findLocation(partner).orElse(null);
        List<DriverLocation> trail = trip.getStatus() == BookingStatus.IN_PROGRESS ? trails.trail(trip.getId()) : List.of();
        return new TripWatchRules.TripFacts(trip.getStatus(), trip.getAcceptedAt(), trip.getStartedAt(),
                trip.getQuotedDistanceKm() == null ? null : trip.getQuotedDistanceKm().doubleValue(),
                trip.getDrop().getLat(), trip.getDrop().getLng(), fix, trail,
                partner == null ? 0 : locations.recentImplausibleJumps(partner));
    }

    @Override
    public List<TripAlert> openAlerts() {
        return alerts.findByResolvedAtIsNullOrderByRaisedAtAsc().stream().map(TripAlertEntity::view).toList();
    }

    @Override
    public List<TripAlert> alertsFor(UUID bookingId) {
        return alerts.findByBookingIdOrderByRaisedAtDesc(bookingId).stream().map(TripAlertEntity::view).toList();
    }

    @Override
    public boolean acknowledge(UUID alertId, UUID adminAccountId, String note) {
        Boolean done = tx.execute(s -> {
            Optional<TripAlertEntity> found = alerts.findById(alertId).filter(a -> a.getResolvedAt() == null);
            found.ifPresent(a -> {
                a.resolve(Instant.now(), adminAccountId, note);
                alerts.save(a);
            });
            return found.isPresent();
        });
        return Boolean.TRUE.equals(done);
    }
}
