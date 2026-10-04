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
 */
final class SellerPlan {

    private SellerPlan() {
    }

    static PlanState state(SellerProfileEntity seller, MarketplaceLimits limits, Instant now) {
        Instant paidUntil = seller.getPaidUntil();
        if (paidUntil == null) {
            return PlanState.NOT_STARTED;
        }
        if (seller.getStatus() == SellerStatus.LAPSED || !now.isBefore(paidUntil.plus(limits.grace()))) {
            return PlanState.LAPSED;
        }
        if (!now.isBefore(paidUntil)) {
            return PlanState.OVERDUE;
        }
        if (!now.isBefore(paidUntil.minus(limits.payWindow()))) {
            return PlanState.DUE_SOON;
        }
        return seller.getPaidPeriods() == 0 ? PlanState.FREE_MONTH : PlanState.PAID;
    }

    /**
     * Whether she may pay for her next month now: in the last days of her
     * month, after it, or once lapsed - never months ahead, and never while
     * a person has suspended her shop.
     */
    static boolean canPay(SellerProfileEntity seller, MarketplaceLimits limits, Instant now) {
        if (seller.getStatus() != SellerStatus.ACTIVE && seller.getStatus() != SellerStatus.LAPSED) {
            return false;
        }
        PlanState state = state(seller, limits, now);
        return state == PlanState.DUE_SOON || state == PlanState.OVERDUE || state == PlanState.LAPSED;
    }

    /** The month her next payment is for: 1 for her first paid month. */
    static int nextPeriod(SellerProfileEntity seller) {
        return seller.getPaidPeriods() + 1;
    }
}
