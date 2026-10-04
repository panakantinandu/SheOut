package com.sheout.payments;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Money in over a period, for the owners' dashboard: what riders paid for
 * trips, SheOut's share of it, listing fees, and refunds credited back to
 * riders' wallets. Totals only - no rider, no trip.
 */
public interface PaymentTotalsApi {

    /**
     * @param fares       captured trip fares
     * @param commission  SheOut's part of those fares (fare minus the partner's share)
     * @param listingFees captured seller listing fees
     * @param refunds     console refunds credited to rider wallets
     */
    record Totals(long trips, BigDecimal fares, BigDecimal commission, BigDecimal listingFees, long refundCount,
                  BigDecimal refunds) {
    }

    /** Captured (or, for refunds, credited) in [from, to). */
    Totals between(Instant from, Instant to);
}
