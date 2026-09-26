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
        /**
         * What the partner receives, and the rate that produced it.
         * <p>
         * Both null until the payment is captured - nothing is owed to
         * anyone before the rider has paid. Kept alongside the full amount
         * rather than replacing it: the rider's price and the platform's
         * margin are two separate numbers, and a partner is entitled to see
         * both rather than being told only what is left.
         */
        BigDecimal driverPayout,
        BigDecimal commissionPercent,
        Instant createdAt,
        Instant updatedAt,
        Instant capturedAt,
        /**
         * The trip's whole fare. The same as amount unless a promotion paid
         * part of it: amount is what the rider was charged, this is what the
         * partner's share and the platform fee were worked out from.
         */
        BigDecimal fareAmount
) {
}
