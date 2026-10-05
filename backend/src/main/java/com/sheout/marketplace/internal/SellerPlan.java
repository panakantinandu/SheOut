package com.sheout.marketplace.internal;

import com.sheout.marketplace.MarketplaceViews.PlanState;
import com.sheout.marketplace.SellerStatus;

import java.time.Instant;

/**
 * Where a seller stands on her monthly plan at a moment: the rules in one
 * place, so the app, the console, the reminders and the payment checks all
 * read the same answer.
 * <p>
 * Her month runs to paidUntil. In the last payWindow of it she may pay for
 * the next; paying adds a month to whichever is later, now or paidUntil, so
 * paying early never loses her a day and paying late never charges her for
 * days she was hidden. For grace after paidUntil her shop is still listed;
 * after that it is LAPSED and hidden until she pays.
 * <p>
 * A plan she stopped has no grace and no "pay now": it is listed to the end
 * of the month she has, then hidden. She can undo the stop until then;
 * afterwards, paying starts it again.
 */
final class SellerPlan {

    private SellerPlan() {
    }

    static PlanState state(SellerProfileEntity seller, MarketplaceLimits limits, Instant now) {
        Instant paidUntil = seller.getPaidUntil();
        if (paidUntil == null) {
            return PlanState.NOT_STARTED;
        }
        if (seller.getStatus() == SellerStatus.LAPSED || !now.isBefore(hiddenFrom(seller, limits))) {
            return PlanState.LAPSED;
        }
        if (!now.isBefore(paidUntil)) {
            return PlanState.OVERDUE;
        }
        // Stopped: nothing is due, so her last days are not "due soon".
        if (!seller.planStopped() && !now.isBefore(paidUntil.minus(limits.payWindow()))) {
            return PlanState.DUE_SOON;
        }
        return seller.getPaidPeriods() == 0 ? PlanState.FREE_MONTH : PlanState.PAID;
    }

    /** When her shop leaves the directory unless she pays: after the grace days, or at once for a stopped plan. */
    static Instant hiddenFrom(SellerProfileEntity seller, MarketplaceLimits limits) {
        Instant paidUntil = seller.getPaidUntil();
        if (paidUntil == null) {
            return null;
        }
        return seller.planStopped() ? paidUntil : paidUntil.plus(limits.grace());
    }

    /**
     * Whether she may pay for her next month now: in the last days of her
     * month, after it, or once lapsed - never months ahead, never while a
     * person has suspended her shop, and not while a plan she stopped is
     * still running (she keeps it first, then pays).
     */
    static boolean canPay(SellerProfileEntity seller, MarketplaceLimits limits, Instant now) {
        if (seller.getStatus() != SellerStatus.ACTIVE && seller.getStatus() != SellerStatus.LAPSED) {
            return false;
        }
        PlanState state = state(seller, limits, now);
        if (seller.planStopped()) {
            return state == PlanState.LAPSED;
        }
        return state == PlanState.DUE_SOON || state == PlanState.OVERDUE || state == PlanState.LAPSED;
    }

    /** Whether she can stop her plan now: a real shop, live, with a month running and not already stopped. */
    static boolean canStop(SellerProfileEntity seller, MarketplaceLimits limits, Instant now) {
        return !seller.isSample() && !seller.planStopped() && seller.getStatus() == SellerStatus.ACTIVE
                && seller.getPaidUntil() != null && now.isBefore(hiddenFrom(seller, limits));
    }

    /** Whether she can take back a stop: still live, so the month she has is still running. */
    static boolean canResume(SellerProfileEntity seller, MarketplaceLimits limits, Instant now) {
        return seller.planStopped() && seller.getStatus() == SellerStatus.ACTIVE
                && seller.getPaidUntil() != null && now.isBefore(hiddenFrom(seller, limits));
    }

    /** The month her next payment is for: 1 for her first paid month. */
    static int nextPeriod(SellerProfileEntity seller) {
        return seller.getPaidPeriods() + 1;
    }
}
