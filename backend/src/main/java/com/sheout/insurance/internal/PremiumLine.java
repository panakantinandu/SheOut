package com.sheout.insurance.internal;

import java.math.BigDecimal;
import java.util.UUID;

/** Trips one policy covered in a window, and the premium SheOut owes for them. */
public record PremiumLine(UUID policyId, long trips, BigDecimal premium) {
}
