package com.sheout.marketplace;

/**
 * Where a seller is, from her first draft to the directory.
 * <pre>
 * DRAFT -> SUBMITTED_FOR_REVIEW -> APPROVED_AWAITING_PAYMENT -> ACTIVE <-> SUSPENDED
 *                              \-> REJECTED -> (edit) -> SUBMITTED_FOR_REVIEW
 * </pre>
 * Reviewing costs her nothing: she pays only once a person has approved
 * what she will list. Only ACTIVE sellers appear in the directory.
 */
public enum SellerStatus {
    DRAFT,
    SUBMITTED_FOR_REVIEW,
    APPROVED_AWAITING_PAYMENT,
    ACTIVE,
    REJECTED,
    SUSPENDED
}
