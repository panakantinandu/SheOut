package com.sheout.campaigns;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What booking asks campaigns: how much of this fare a promotion will pay.
 * The fare itself is booking's and is never changed here.
 */
public interface CampaignsApi {

    /** The best promotion this rider holds for this fare, without using anything - for a quote. */
    PromoApplication previewDiscount(UUID customerId, BigDecimal fare);

    /**
     * Uses it for this booking: the credit or use is held and the amount
     * counted against the promotion's budget, in the caller's transaction.
     * Given back if the trip is cancelled or finds no partner; kept when it
     * completes.
     */
    PromoApplication reserveDiscount(UUID customerId, UUID bookingId, BigDecimal fare);
}
