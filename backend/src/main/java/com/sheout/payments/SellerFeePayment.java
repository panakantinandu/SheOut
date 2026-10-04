package com.sheout.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One month a seller paid for, for her payment history. period 1 is her
 * first paid month; 0 is the one-time fee from before monthly plans.
 */
public record SellerFeePayment(UUID paymentId, int period, BigDecimal amount, PaymentMethod method, Instant paidAt) {
}
