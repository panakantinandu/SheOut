package com.sheout.payouts.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** One movement in a partner's wallet. Append-only - no setters. */
@Entity
@Table(name = "wallet_entries")
public class WalletEntryEntity extends BaseEntity {

    enum Type {
        /** Her share of a captured trip. Positive. */
        EARNING,
        /** A rider paid her cash directly - the whole fare. Negative. */
        CASH_COLLECTED,
        /** She asked to be paid; the amount is held. Negative. */
        PAYOUT_REQUESTED
    }

    @Column(nullable = false)
    private UUID driverAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private Type type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    private UUID bookingId;

    private UUID paymentId;

    private UUID payoutRequestId;

    protected WalletEntryEntity() {
        // JPA
    }

    static WalletEntryEntity forPayment(UUID driverAccountId, Type type, BigDecimal signedAmount, UUID bookingId, UUID paymentId) {
        WalletEntryEntity e = new WalletEntryEntity();
        e.driverAccountId = driverAccountId;
        e.type = type;
        e.amount = signedAmount;
        e.bookingId = bookingId;
        e.paymentId = paymentId;
        return e;
    }

    static WalletEntryEntity forPayoutRequest(UUID driverAccountId, BigDecimal amount, UUID payoutRequestId) {
        WalletEntryEntity e = new WalletEntryEntity();
        e.driverAccountId = driverAccountId;
        e.type = Type.PAYOUT_REQUESTED;
        e.amount = amount.negate();
        e.payoutRequestId = payoutRequestId;
        return e;
    }
}
