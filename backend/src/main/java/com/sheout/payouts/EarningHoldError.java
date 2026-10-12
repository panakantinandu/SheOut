package com.sheout.payouts;

/** Why an operator's hold, release or withhold was refused. */
public enum EarningHoldError {
    /** No parcel earnings for this trip - not a parcel, or not paid yet. */
    NOT_FOUND,
    /** Already released or withheld; that is final. */
    ALREADY_SETTLED,
    REASON_REQUIRED
}
