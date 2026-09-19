package com.sheout.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A trip that has ended and is still waiting for the rider's payment.
 * <p>
 * For a rider, it blocks her next booking for as long as it stands. For a
 * partner, it keeps new offers away until {@code holdUntil}: she should see
 * the fare land before driving off to somebody else, but a rider who walks
 * away without paying must not be able to take her off the road for the rest
 * of the day. After holdUntil she is offered work again and the debt stays
 * with the rider, who cannot book until it is paid - at which point the
 * partner is credited as usual. {@code holdUntil} is null on a rider's hold,
 * which has no end short of payment.
 */
public record PaymentHold(UUID bookingId, BigDecimal amount, Instant completedAt, Instant holdUntil) {
}
