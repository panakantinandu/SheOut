package com.sheout.payouts;

import java.math.BigDecimal;

/**
 * A partner's money, as figures she can check against each other.
 * <p>
 * totalEarned is her share of every captured trip, cash or online.
 * cashCollected is what riders paid her directly - already in her hands, so
 * it comes off what SheOut still owes. availableBalance is what she can ask
 * to be paid now, and can be negative: a run of cash trips leaves her owing
 * the platform's commission on them, which later online trips settle.
 */
public record WalletSummary(
        BigDecimal totalEarned,
        BigDecimal cashCollected,
        BigDecimal totalPaidOut,
        BigDecimal pendingPayouts,
        BigDecimal availableBalance
) {
}
