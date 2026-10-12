package com.sheout.payouts.internal;

import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingSummary;
import com.sheout.booking.BookingType;
import com.sheout.booking.ParcelDeliveryApi;
import com.sheout.booking.ParcelDisputeClosed;
import com.sheout.booking.ParcelDisputeOpened;
import com.sheout.booking.ParcelDisputeOutcome;
import com.sheout.payouts.EarningHold;
import com.sheout.payouts.EarningHoldApi;
import com.sheout.payouts.EarningHoldError;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.cluster.ClusterLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A partner's earnings from a parcel wait PARCEL_DISPUTE_WINDOW_HOURS (24)
 * after delivery before she can withdraw them. Credited at capture as
 * always, so her statement shows the trip at once, and counted as held, so
 * it is not yet in her available balance. The release sweep frees it when
 * the window has passed with no dispute and no operator holding it.
 * <p>
 * An operator can hold it longer (with a reason) or release it now. A
 * dispute holds it until the dispute is closed; closed against her, it is
 * withheld - taken off what she earned, never paid.
 */
@Service
public class EarningHoldService implements EarningHoldApi {

    private static final Logger log = LoggerFactory.getLogger(EarningHoldService.class);

    private final EarningHoldRepository holds;
    private final DriverWalletRepository wallets;
    private final WalletEntryRepository entries;
    private final BookingApi bookings;
    private final ParcelDeliveryApi parcels;
    private final Duration window;
    private final ClusterLock lock;
    private final TransactionTemplate transactions;

    public EarningHoldService(EarningHoldRepository holds, DriverWalletRepository wallets, WalletEntryRepository entries,
                              BookingApi bookings, ParcelDeliveryApi parcels, ClusterLock lock,
                              TransactionTemplate transactions,
                              @Value("${sheout.parcel.dispute-window-hours:24}") long windowHours) {
        this.holds = holds;
        this.wallets = wallets;
        this.entries = entries;
        this.bookings = bookings;
        this.parcels = parcels;
        this.lock = lock;
        this.transactions = transactions;
        this.window = Duration.ofHours(windowHours);
    }

    /**
     * Called by PayoutService inside the capture's own transaction, with her
     * wallet already locked and credited: a parcel's share is held as well.
     * Held from delivery (or from now, if payment came later), so a sender
     * who pays late does not shorten the window.
     */
    void holdIfParcel(DriverWalletEntity lockedWallet, UUID driverId, UUID bookingId, BigDecimal share) {
        Optional<BookingSummary> booking = bookings.findById(bookingId);
        if (booking.isEmpty() || booking.get().type() != BookingType.DELIVERY || holds.existsById(bookingId)) {
            return;
        }
        Instant now = Instant.now();
        Instant deliveredAt = booking.get().completedAt() == null ? now : booking.get().completedAt();
        Instant releaseAt = (deliveredAt.isAfter(now) ? deliveredAt : now).plus(window);
        boolean disputed = parcels.evidence(bookingId)
                .map(e -> e.disputeOpenedAt() != null && e.disputeClosedAt() == null).orElse(false);
        lockedWallet.hold(share);
        holds.save(new EarningHoldEntity(bookingId, driverId, share, releaseAt, disputed,
                disputed ? "Dispute open" : null));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EarningHold> find(UUID bookingId) {
        return holds.findById(bookingId).map(EarningHoldService::toView);
    }

    @Override
    @Transactional
    public Result<EarningHold, EarningHoldError> hold(UUID bookingId, UUID adminAccountId, String reason) {
        String why = trim(reason);
        if (why == null) {
            return Result.failure(EarningHoldError.REASON_REQUIRED);
        }
        Optional<EarningHoldEntity> found = holds.findLockedByBookingId(bookingId);
        if (found.isEmpty()) {
            return Result.failure(EarningHoldError.NOT_FOUND);
        }
        EarningHoldEntity h = found.get();
        if (h.getStatus() != EarningHoldEntity.Status.HELD) {
            return Result.failure(EarningHoldError.ALREADY_SETTLED);
        }
        h.holdByOperator(adminAccountId, why, Instant.now());
        holds.save(h);
        return Result.success(toView(h));
    }

    @Override
    @Transactional
    public Result<EarningHold, EarningHoldError> release(UUID bookingId, UUID adminAccountId, String reason) {
        String why = trim(reason);
        if (why == null) {
            return Result.failure(EarningHoldError.REASON_REQUIRED);
        }
        return settle(bookingId, EarningHoldEntity.Status.RELEASED, adminAccountId, why);
    }

    /** A dispute keeps her earnings held until it is closed, whatever the window says. */
    @EventListener
    @Transactional
    public void onDisputeOpened(ParcelDisputeOpened event) {
        holds.findLockedByBookingId(event.bookingId())
                .filter(h -> h.getStatus() == EarningHoldEntity.Status.HELD)
                .ifPresent(h -> {
                    h.holdByOperator(null, "Dispute open", Instant.now());
                    holds.save(h);
                });
    }

    @EventListener
    @Transactional
    public void onDisputeClosed(ParcelDisputeClosed event) {
        EarningHoldEntity.Status outcome = event.outcome() == ParcelDisputeOutcome.PARTNER_AT_FAULT
                ? EarningHoldEntity.Status.WITHHELD : EarningHoldEntity.Status.RELEASED;
        Result<EarningHold, EarningHoldError> r = settle(event.bookingId(), outcome, event.decidedBy(),
                "Dispute closed: " + (event.note() == null ? event.outcome().name() : event.note()));
        if (r.isFailure() && r.error() != EarningHoldError.NOT_FOUND) {
            log.warn("Dispute on {} closed but its earnings were already {}", event.bookingId(), r.error());
        }
    }

    private Result<EarningHold, EarningHoldError> settle(UUID bookingId, EarningHoldEntity.Status outcome, UUID by, String reason) {
        Optional<EarningHoldEntity> found = holds.findLockedByBookingId(bookingId);
        if (found.isEmpty()) {
            return Result.failure(EarningHoldError.NOT_FOUND);
        }
        EarningHoldEntity h = found.get();
        if (h.getStatus() != EarningHoldEntity.Status.HELD) {
            return Result.failure(EarningHoldError.ALREADY_SETTLED);
        }
        wallets.createIfAbsent(h.getDriverAccountId());
        DriverWalletEntity wallet = wallets.findLockedByDriverAccountId(h.getDriverAccountId()).orElseThrow();
        if (outcome == EarningHoldEntity.Status.WITHHELD) {
            wallet.withholdHeld(h.getAmount());
            entries.save(WalletEntryEntity.forPayment(h.getDriverAccountId(), WalletEntryEntity.Type.EARNING_WITHHELD,
                    h.getAmount().negate(), bookingId, null));
        } else {
            wallet.releaseHeld(h.getAmount());
        }
        wallets.save(wallet);
        h.settle(outcome, by, reason, Instant.now());
        holds.save(h);
        return Result.success(toView(h));
    }

    /** Frees every hold whose window has passed and that nobody is keeping. One transaction per hold. */
    public int releaseDue(Instant now) {
        int released = 0;
        for (EarningHoldEntity due : holds.findTop200ByStatusAndOperatorHoldFalseAndReleaseAtBefore(EarningHoldEntity.Status.HELD, now)) {
            Boolean ok = transactions.execute(s -> {
                // Re-read under the lock: an operator may have held it since the list was taken.
                Optional<EarningHoldEntity> fresh = holds.findLockedByBookingId(due.getBookingId());
                if (fresh.isEmpty() || fresh.get().isOperatorHold() || fresh.get().getStatus() != EarningHoldEntity.Status.HELD) {
                    return false;
                }
                return settle(due.getBookingId(), EarningHoldEntity.Status.RELEASED, null, null).isSuccess();
            });
            if (Boolean.TRUE.equals(ok)) {
                released++;
            }
        }
        return released;
    }

    @Scheduled(fixedDelayString = "${sheout.parcel.hold-release-interval-ms:300000}", initialDelayString = "${sheout.parcel.hold-release-initial-delay-ms:120000}")
    public void releaseSweep() {
        lock.runExclusively("parcel-earning-release", Duration.ofMinutes(5), () -> {
            int n = releaseDue(Instant.now());
            if (n > 0) {
                log.info("Released {} parcel earnings past their dispute window", n);
            }
        });
    }

    private static EarningHold toView(EarningHoldEntity h) {
        return new EarningHold(h.getBookingId(), h.getDriverAccountId(), h.getAmount(), h.getReleaseAt(),
                h.getStatus().name(), h.isOperatorHold(), h.getReason(), h.getDecidedAt());
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim().length() > 500 ? s.trim().substring(0, 500) : s.trim();
    }
}
