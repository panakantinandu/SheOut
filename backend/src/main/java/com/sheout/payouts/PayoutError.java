package com.sheout.payouts;

public enum PayoutError {
    /** Neither a complete bank account nor a UPI VPA is saved. */
    NO_PAYOUT_DETAILS,
    /** A bank field or the VPA is malformed, or bank details are partial. */
    INVALID_DETAILS,
    /** Zero, negative, or more than two decimal places. */
    INVALID_AMOUNT,
    /** More than the available balance. */
    INSUFFICIENT_BALANCE,
    /** No such request. */
    REQUEST_NOT_FOUND,
    /** Already PAID - a second "mark paid" must not rewrite who paid it or the reference. */
    ALREADY_PAID,
    /** Marking paid without a transaction reference. */
    REFERENCE_REQUIRED
}
