package com.sheout.payouts;

/**
 * PENDING until an operator has sent the money and recorded the reference;
 * then PAID, which is final.
 */
public enum PayoutStatus {
    PENDING,
    PAID
}
