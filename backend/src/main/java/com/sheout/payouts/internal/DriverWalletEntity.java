package com.sheout.payouts.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One partner's running totals. Changed only through the methods below,
 * each of which PayoutService pairs with a WalletEntryEntity, so the row and
 * the ledger never disagree. Always read under a row lock before changing.
 */
@Entity
@Table(name = "driver_wallets")
public class DriverWalletEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID driverAccountId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEarned = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cashCollected = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPaidOut = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pendingPayouts = BigDecimal.ZERO;

    @Version
    private long version;

    protected DriverWalletEntity() {
        // JPA
    }

    public BigDecimal available() {
        return totalEarned.subtract(cashCollected).subtract(totalPaidOut).subtract(pendingPayouts);
    }

    void creditEarning(BigDecimal driverShare) {
        totalEarned = totalEarned.add(driverShare);
    }

    void recordCashCollected(BigDecimal fare) {
        cashCollected = cashCollected.add(fare);
    }

    void holdForPayout(BigDecimal amount) {
        pendingPayouts = pendingPayouts.add(amount);
    }

    void settlePayout(BigDecimal amount) {
        pendingPayouts = pendingPayouts.subtract(amount);
        totalPaidOut = totalPaidOut.add(amount);
    }

    public UUID getDriverAccountId() {
        return driverAccountId;
    }

    public BigDecimal getTotalEarned() {
        return totalEarned;
    }

    public BigDecimal getCashCollected() {
        return cashCollected;
    }

    public BigDecimal getTotalPaidOut() {
        return totalPaidOut;
    }

    public BigDecimal getPendingPayouts() {
        return pendingPayouts;
    }
}
