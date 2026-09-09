package com.sheout.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of a booking's payment. razorpayOrderId/razorpayPaymentId/
 * failureReason are gateway plumbing rather than something explicitly
 * requested on this record - ASSUMPTION FLAGGED: included anyway because a
 * Wallet/payment-status screen needs razorpayOrderId to open Razorpay
 * checkout for a PENDING UPI payment, and a failure reason to show the
 * customer why a payment failed. Both are null for a CASH payment.
 */
public record PaymentSummary(
        UUID id,
        UUID bookingId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String razorpayOrderId,
        String razorpayPaymentId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Instant capturedAt
) {
}
