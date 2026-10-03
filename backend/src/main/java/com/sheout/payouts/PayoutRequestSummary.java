package com.sheout.payouts;

import com.sheout.sharedkernel.privacy.Pii;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One payout request, with the destination as it was when she asked.
 * paidAt, paidBy and paymentReference are null until PAID.
 */
public record PayoutRequestSummary(
        UUID id,
        UUID driverAccountId,
        BigDecimal amount,
        PayoutStatus status,
        String accountHolderName,
        @Pii(Pii.Kind.BANK) String accountNumber,
        String ifsc,
        @Pii(Pii.Kind.BANK) String upiVpa,
        Instant requestedAt,
        Instant paidAt,
        UUID paidBy,
        String paymentReference
) {
}
