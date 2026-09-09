package com.sheout.payments;

import com.sheout.sharedkernel.Result;

import java.util.UUID;

/**
 * Deliberately minimal, per spec: the two operations a frontend payment-
 * status/Wallet screen needs. Payment creation itself is not here - it is
 * driven by BookingCompleted (see BookingCompletedListener, internal), not
 * called directly by another module, so that booking never needs to know
 * payments exists.
 * <p>
 * ASSUMPTION FLAGGED on initiateCashPayment: it only succeeds once a
 * payment row already exists for the booking (i.e. after BookingCompleted
 * has been processed) - it flips that row's method to CASH and marks it
 * captured, rather than creating a fresh record from just a bookingId. This
 * is because the amount to charge (finalFare) is only known from
 * BookingCompleted's payload; BookingApi has no read method today (see its
 * Javadoc) for this module to fetch it independently. A caller (e.g. a
 * driver-app "collected cash" action) is expected to call this only after
 * the booking is COMPLETED. Returns PAYMENT_NOT_FOUND otherwise.
 */
public interface PaymentApi {

    Result<PaymentSummary, PaymentError> getPaymentStatus(UUID bookingId);

    Result<PaymentSummary, PaymentError> initiateCashPayment(UUID bookingId);
}
