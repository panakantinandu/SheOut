package com.sheout.marketplace;

/**
 * Where a seller is, from her first draft to the directory.
 * <pre>
 * DRAFT -> SUBMITTED_FOR_REVIEW -> ACTIVE (first month free) <-> SUSPENDED
 *                              \-> REJECTED      ACTIVE <-> LAPSED (month not paid / paid again)
 * </pre>
 * Reviewing costs her nothing, and approval puts her shop live at once on
 * its free month. Only ACTIVE sellers appear in the directory.
 * APPROVED_AWAITING_PAYMENT is from before monthly plans and no longer used.
 */
public enum SellerStatus {
    DRAFT,
    SUBMITTED_FOR_REVIEW,
    APPROVED_AWAITING_PAYMENT,
    ACTIVE,
    REJECTED,
    SUSPENDED,
    /** Her paid month ended and the grace days passed: hidden until she pays for the next. Nothing is deleted. */
    LAPSED
}
