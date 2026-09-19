package com.sheout.payments;

import com.sheout.sharedkernel.Result;

import java.util.UUID;

/**
 * Deliberately minimal: what another module needs to know about a trip's
 * payment. Payment creation is not here - it is driven by BookingCompleted
 * (see BookingCompletedListener, internal), so booking never needs to know
 * payments exists.
 * <p>
 * There is no cash method any more. Every fare is paid through SheOut - from
 * the rider's wallet or online - so it reaches the partner's wallet with a
 * record behind it, and nobody can mark a fare paid by tapping a button.
 */
public interface PaymentApi {

    Result<PaymentSummary, PaymentError> getPaymentStatus(UUID bookingId);
}
