package com.sheout.admin.internal;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingType;
import com.sheout.payments.PaymentMethod;
import com.sheout.payments.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A booking and its payment side by side. paymentStatus/paymentMethod are
 * null when no payment row exists yet, which is every booking that has not
 * completed - not an error state.
 */
public record BookingOpsRow(
        UUID bookingId,
        BookingStatus status,
        BookingType type,
        BookingCategory category,
        String customerName,
        String driverName,
        BigDecimal fareEstimate,
        BigDecimal finalFare,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        Instant requestedAt,
        Instant completedAt
) {
}
