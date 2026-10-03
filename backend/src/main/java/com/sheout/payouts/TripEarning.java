package com.sheout.payouts;

import java.math.BigDecimal;
import java.util.UUID;

/** What a partner was credited for one trip: her share of the fare, after SheOut's commission. */
public record TripEarning(UUID bookingId, BigDecimal share) {
}
