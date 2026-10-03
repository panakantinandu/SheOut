package com.sheout.staff;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * How much a member of staff may refund, or credit as goodwill, on her own
 * say-so, in rupees. Above it the refund needs an OWNER's approval.
 * <p>
 * The founder's limits (2026-10-03): support agents ₹200 (REFUND_LIMIT_SUPPORT),
 * the operations manager ₹1,000 (REFUND_LIMIT_MANAGER). Finance holds
 * refunds.issue too but was given no figure; it gets the support limit
 * (REFUND_LIMIT_FINANCE) until someone decides otherwise. Owners have no
 * limit; a role without refunds.issue has none to spend at all.
 * <p>
 * Nothing issues refunds from the console yet. The refund endpoint arrives
 * with the approval queue (Phase 3) and asks this before it pays anything.
 */
public interface RefundLimits {

    /** Empty means unlimited (OWNER). Zero for a role that cannot refund at all. */
    Optional<BigDecimal> limitFor(StaffRole role);

    /** Whether this amount can be refunded by this person without an owner's approval. */
    default boolean withinOwnLimit(StaffPrincipal staff, BigDecimal amount) {
        if (!staff.has(Permission.REFUNDS_ISSUE) || amount == null || amount.signum() <= 0) {
            return false;
        }
        return limitFor(staff.role()).map(limit -> amount.compareTo(limit) <= 0).orElse(true);
    }
}
