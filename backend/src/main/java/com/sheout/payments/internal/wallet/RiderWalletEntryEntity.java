package com.sheout.payments.internal.wallet;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** One movement on a rider's balance. Append-only - no setters. */
@Entity
@Table(name = "rider_wallet_entries")
public class RiderWalletEntryEntity extends BaseEntity {

    public enum Type {
        /** Money she added through Razorpay. Positive. */
        TOPUP,
        /** A trip she paid from her balance. Negative. */
        TRIP_PAYMENT
    }

    @Column(nullable = false)
    private UUID customerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private Type type;

    /** Signed: positive adds to her balance, negative takes from it. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    private UUID bookingId;

    private UUID topupId;

    protected RiderWalletEntryEntity() {
        // JPA
    }

    static RiderWalletEntryEntity topup(UUID customerAccountId, BigDecimal amount, BigDecimal balanceAfter, UUID topupId) {
        RiderWalletEntryEntity e = new RiderWalletEntryEntity();
        e.customerAccountId = customerAccountId;
        e.type = Type.TOPUP;
        e.amount = amount;
        e.balanceAfter = balanceAfter;
        e.topupId = topupId;
        return e;
    }

    static RiderWalletEntryEntity tripPayment(UUID customerAccountId, BigDecimal amount, BigDecimal balanceAfter,
                                              UUID bookingId) {
        RiderWalletEntryEntity e = new RiderWalletEntryEntity();
        e.customerAccountId = customerAccountId;
        e.type = Type.TRIP_PAYMENT;
        e.amount = amount.negate();
        e.balanceAfter = balanceAfter;
        e.bookingId = bookingId;
        return e;
    }

    public UUID getCustomerAccountId() {
        return customerAccountId;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getTopupId() {
        return topupId;
    }
}
