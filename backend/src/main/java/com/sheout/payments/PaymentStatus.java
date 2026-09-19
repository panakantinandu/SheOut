package com.sheout.payments;

/**
 * PENDING -&gt; CAPTURED (Razorpay confirms, or the rider pays from her SheOut
 * wallet) or -&gt; FAILED, which can still be retried. Terminal once CAPTURED -
 * see PaymentService.
 * <p>
 * WAIVED is written only by migration V23, for trips that ended before a trip
 * had to be paid to be closed. It counts as settled and is never set by the
 * application.
 */
public enum PaymentStatus {
    PENDING,
    CAPTURED,
    FAILED,
    WAIVED
}
