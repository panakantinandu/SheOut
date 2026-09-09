package com.sheout.payments;

/** PENDING -&gt; CAPTURED (gateway webhook confirms, or cash is confirmed collected) or -&gt; FAILED. Terminal once CAPTURED/FAILED - see PaymentService. */
public enum PaymentStatus {
    PENDING,
    CAPTURED,
    FAILED
}
