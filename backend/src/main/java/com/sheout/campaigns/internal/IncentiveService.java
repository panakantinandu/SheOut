package com.sheout.campaigns.internal;

import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingParticipants;
import com.sheout.campaigns.DriverIncentiveAwarded;
import com.sheout.payments.PaymentCaptured;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Pays partner incentives on each paid trip.
 * <p>
 * On a captured payment - a trip that has actually been paid for, never one
 * still owed - every running incentive is asked what it pays on this trip.
 * Each award is written, counted against that incentive's budget under a
 * lock, and published; payouts credits her wallet from the event in the same
 * transaction. An award is unique per incentive and trip, so a replayed
 * event cannot pay twice.
 * <p>
 * BookingApi is looked up lazily: booking itself asks campaigns for
 * discounts, and the two must not need each other to be built first.
 */
@Service
public class IncentiveService {

    private static final Logger log = LoggerFactory.getLogger(IncentiveService.class);

    private final DriverIncentiveRepository incentives;
    private final IncentiveAwardRepository awards;
    private final ObjectProvider<BookingApi> bookingApi;
    private final DomainEventPublisher events;

    public IncentiveService(DriverIncentiveRepository incentives, IncentiveAwardRepository awards,
                            ObjectProvider<BookingApi> bookingApi, DomainEventPublisher events) {
        this.incentives = incentives;
        this.awards = awards;
        this.bookingApi = bookingApi;
        this.events = events;
    }

    @EventListener
    @Transactional
    public void onPaymentCaptured(PaymentCaptured event) {
        Instant now = Instant.now();
        var running = incentives.findAllByOrderByCreatedAtDesc().stream().filter(i -> i.activeAt(now)).toList();
        if (running.isEmpty()) {
            return;
        }
        BookingApi bookings = bookingApi.getObject();
        Result<BookingParticipants, BookingError> participants = bookings.getParticipants(event.bookingId());
        if (participants.isFailure() || participants.value().driverId() == null) {
            return;
        }
        UUID driverId = participants.value().driverId();
        IncentiveRules.Trip trip = new IncentiveRules.Trip(
                event.driverPayout() == null ? BigDecimal.ZERO : event.driverPayout(),
                bookings.completedTripOrdinalForDriver(driverId, event.bookingId()));

        for (DriverIncentiveEntity candidate : running) {
            if (awards.existsByIncentiveIdAndBookingId(candidate.getId(), event.bookingId())) {
                continue;
            }
            DriverIncentiveEntity incentive = incentives.findLockedById(candidate.getId()).orElse(null);
            if (incentive == null || !incentive.activeAt(now)) {
                continue;
            }
            BigDecimal amount = incentive.affordable(IncentiveRules.amountFor(incentive, trip))
                    .setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) {
                continue;
            }
            incentive.spend(amount, now);
            incentives.save(incentive);
            IncentiveAwardEntity award = awards.save(new IncentiveAwardEntity(incentive.getId(), driverId, event.bookingId(), amount));
            events.publish(new DriverIncentiveAwarded(award.getId(), driverId, event.bookingId(), amount, incentive.getName()));
            log.info("Incentive '{}' paid {} to partner {} for trip {} (her trip #{})",
                    incentive.getName(), amount, driverId, event.bookingId(), trip.tripOrdinal());
        }
    }
}
