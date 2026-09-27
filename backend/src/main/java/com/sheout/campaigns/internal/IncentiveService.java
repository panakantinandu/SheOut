package com.sheout.campaigns.internal;

import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingParticipants;
import com.sheout.campaigns.DriverIncentiveAwarded;
import com.sheout.campaigns.IncentiveType;
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
import java.util.Comparator;
import java.util.Optional;
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

    /**
     * A partner referral, paid once: to the partner who referred (REFERRAL_REWARD)
     * or to the friend she referred (REFERRAL_WELCOME), on the friend's first
     * paid trip. The same path as every incentive - the running incentive of
     * that type, locked, the amount cut to what its budget has left and
     * counted against it, an award keyed on the trip so it cannot be paid
     * twice, and the event payouts credits her wallet from.
     * <p>
     * Nothing when no such incentive is running. Returns what was paid.
     */
    @Transactional
    public BigDecimal awardReferral(IncentiveType type, UUID driverId, UUID bookingId, Instant now) {
        if (type != IncentiveType.REFERRAL_REWARD && type != IncentiveType.REFERRAL_WELCOME) {
            throw new IllegalArgumentException("Not a referral incentive: " + type);
        }
        Optional<DriverIncentiveEntity> running = runningReferralIncentive(type, now);
        if (running.isEmpty() || awards.existsByIncentiveIdAndBookingId(running.get().getId(), bookingId)) {
            return BigDecimal.ZERO;
        }
        DriverIncentiveEntity incentive = incentives.findLockedById(running.get().getId()).orElse(null);
        if (incentive == null || !incentive.activeAt(now)) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = incentive.affordable(incentive.getValue()).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        incentive.spend(amount, now);
        incentives.save(incentive);
        IncentiveAwardEntity award = awards.save(new IncentiveAwardEntity(incentive.getId(), driverId, bookingId, amount));
        events.publish(new DriverIncentiveAwarded(award.getId(), driverId, bookingId, amount, incentive.getName()));
        log.info("Referral incentive '{}' paid {} to partner {} (friend's first paid trip {})",
                incentive.getName(), amount, driverId, bookingId);
        return amount;
    }

    /** What the running incentive of this type pays, for the Refer screen; empty when none is running. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> referralAmount(IncentiveType type) {
        return runningReferralIncentive(type, Instant.now()).map(DriverIncentiveEntity::getValue);
    }

    private Optional<DriverIncentiveEntity> runningReferralIncentive(IncentiveType type, Instant now) {
        return incentives.findAllByOrderByCreatedAtDesc().stream()
                .filter(i -> i.getType() == type && i.activeAt(now))
                .max(Comparator.comparing(DriverIncentiveEntity::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
    }
}
