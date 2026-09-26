package com.sheout.campaigns;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a promotion pays towards one fare. discount is zero (and the rest
 * null) when nothing applies.
 */
public record PromoApplication(BigDecimal discount, UUID promotionId, String promotionName) {

    public static PromoApplication none() {
        return new PromoApplication(BigDecimal.ZERO, null, null);
    }

    public boolean applies() {
        return discount.signum() > 0;
    }
}
