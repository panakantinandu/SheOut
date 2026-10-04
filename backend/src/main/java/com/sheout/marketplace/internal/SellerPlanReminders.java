package com.sheout.marketplace.internal;

import com.sheout.marketplace.SellerPlanNotice;
import com.sheout.marketplace.SellerStatus;
import com.sheout.sharedkernel.cluster.ClusterLock;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Keeps sellers' monthly plans moving: hides a shop whose month and grace
 * days are over, and tells her before and after her month ends.
 * <p>
 * Reminders, each once a month: {@code reminder-days} before her month ends
 * (7, 3 and 1 by default), the day it ends ("still listed until..."), and
 * the day her shop is hidden. Only the latest one due is sent, so a server
 * that was down for a few days does not send three at once. They go only in
 * the daytime in India; a shop is hidden at any hour, and told in the
 * morning.
 * <p>
 * Push and her inbox, with email when she has one - never SMS, which needs
 * a DLT-registered template. Hourly, one server at a time.
 */
@Component
class SellerPlanReminders {

    private static final Logger log = LoggerFactory.getLogger(SellerPlanReminders.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    static final String ENDED = "ENDED";
    static final String HIDDEN = "HIDDEN";

    private final SellerProfileRepository sellers;
    private final MarketplaceLimits limits;
    private final DomainEventPublisher events;
    private final ClusterLock lock;
    private final TransactionTemplate tx;
    private final int fromHour;
    private final int toHour;

    SellerPlanReminders(SellerProfileRepository sellers, MarketplaceLimits limits, DomainEventPublisher events,
                        ClusterLock lock, PlatformTransactionManager transactions,
                        @Value("${sheout.marketplace.reminder-from-hour:9}") int fromHour,
                        @Value("${sheout.marketplace.reminder-to-hour:20}") int toHour) {
        this.sellers = sellers;
        this.limits = limits;
        this.events = events;
        this.lock = lock;
        this.tx = new TransactionTemplate(transactions);
        this.fromHour = fromHour;
        this.toHour = toHour;
    }

    @Scheduled(initialDelayString = "${sheout.marketplace.plan-sweep-initial-delay-ms:120000}",
            fixedDelayString = "${sheout.marketplace.plan-sweep-interval-ms:3600000}")
    void sweep() {
        lock.runExclusively("seller-plan", Duration.ofMinutes(10), () -> runOnce(Instant.now()));
    }

    /** One pass. Package-visible for tests, which pass the clock. Returns how many reminders were sent. */
    int runOnce(Instant now) {
        int hour = now.atZone(INDIA).getHour();
        boolean daytime = hour >= fromHour && hour < toHour;
        int sent = 0;
        for (SellerProfileEntity candidate : sellers.findByStatusInAndPaidUntilIsNotNull(
                EnumSet.of(SellerStatus.ACTIVE, SellerStatus.LAPSED))) {
            try {
                Boolean told = tx.execute(s -> step(candidate.getId(), now, daytime));
                if (Boolean.TRUE.equals(told)) {
                    sent++;
                }
            } catch (RuntimeException ex) {
                // One odd shop must not stop the others being looked after.
                log.error("Seller plan step failed for {}: {}", candidate.getId(), ex.toString());
            }
        }
        return sent;
    }

    /** Under the seller's lock, so a payment arriving at the same moment is never hidden by mistake. */
    private boolean step(UUID sellerId, Instant now, boolean daytime) {
        SellerProfileEntity seller = sellers.findLockedById(sellerId).orElse(null);
        if (seller == null || seller.getPaidUntil() == null) {
            return false;
        }
        Instant paidUntil = seller.getPaidUntil();
        Instant hiddenFrom = paidUntil.plus(limits.grace());
        if (seller.getStatus() == SellerStatus.ACTIVE && !now.isBefore(hiddenFrom)) {
            seller.lapse();
            log.info("Seller {} hidden: month ended {} and was not paid", seller.getId(), paidUntil);
        }
        String due = noticeDue(seller, now);
        if (due == null || !daytime || !later(due, seller.getLastPlanNotice())) {
            sellers.save(seller);
            return false;
        }
        seller.planNoticeSent(due);
        sellers.save(seller);
        SellerPlanNotice.Kind kind = HIDDEN.equals(due) ? SellerPlanNotice.Kind.HIDDEN
                : ENDED.equals(due) ? SellerPlanNotice.Kind.ENDED : SellerPlanNotice.Kind.ENDS_SOON;
        int daysLeft = (int) Math.max(0, Math.ceil(Duration.between(now, paidUntil).toMinutes() / 1440.0));
        events.publish(new SellerPlanNotice(seller.getId(), seller.getAccountId(), kind, daysLeft,
                seller.getPaidPeriods() == 0, paidUntil, hiddenFrom, limits.monthlyFee()));
        return true;
    }

    /**
     * The latest reminder due now, or null: "D7", "D3", "D1" (the smallest
     * reminder day not yet past), ENDED once her month is over, HIDDEN once
     * her shop is.
     */
    String noticeDue(SellerProfileEntity seller, Instant now) {
        if (seller.getStatus() == SellerStatus.LAPSED) {
            return HIDDEN;
        }
        Instant paidUntil = seller.getPaidUntil();
        if (paidUntil == null) {
            // No month at all: not approved yet, or a sample shop.
            return null;
        }
        if (!now.isBefore(paidUntil)) {
            return ENDED;
        }
        double daysLeft = Duration.between(now, paidUntil).toMinutes() / 1440.0;
        String due = null;
        for (int day : limits.reminderDays()) { // largest first
            if (daysLeft <= day) {
                due = "D" + day;
            }
        }
        return due;
    }

    /** Whether {@code due} comes after {@code last} in the month's run of reminders. */
    static boolean later(String due, String last) {
        return rank(due) > rank(last);
    }

    private static int rank(String notice) {
        if (notice == null) {
            return 0;
        }
        if (HIDDEN.equals(notice)) {
            return 3000;
        }
        if (ENDED.equals(notice)) {
            return 2000;
        }
        // D7 before D3 before D1.
        return 1000 - Integer.parseInt(notice.substring(1));
    }
}
