package com.sheout.payments.internal.wallet;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A rider's SheOut balance. Changed only through credit and debit, each of
 * which RiderWalletService pairs with a RiderWalletEntryEntity, so the row
 * and the statement never disagree. Always read under a row lock before
 * changing - and the database refuses a negative balance regardless (V23).
 */
@Entity
@Table(name = "rider_wallets")
public class RiderWalletEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID customerAccountId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Version
    private long version;

    protected RiderWalletEntity() {
        // JPA
    }

    RiderWalletEntity(UUID customerAccountId) {
        this.customerAccountId = customerAccountId;
    }

    public UUID getCustomerAccountId() {
        return customerAccountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    void debit(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Debit larger than balance");
        }
        balance = balance.subtract(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Wallet movements are positive amounts");
        }
    }
}
