package com.sheout.payouts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A partner's earnings from one parcel, held until the dispute window has
 * passed with no complaint. HELD, then RELEASED (into her available balance)
 * or WITHHELD (an operator found her at fault). operatorHold: an operator
 * is holding it past the window, with the reason given.
 */
public record EarningHold(UUID bookingId, UUID driverAccountId, BigDecimal amount, Instant releaseAt, String status,
                          boolean operatorHold, String reason, Instant decidedAt) {
}
