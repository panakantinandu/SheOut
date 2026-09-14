package com.sheout.payouts.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "payout_accounts")
public class PayoutAccountEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID driverAccountId;

    @Column(length = 100)
    private String accountHolderName;

    @Column(length = 18)
    private String accountNumber;

    @Column(length = 11)
    private String ifsc;

    @Column(length = 100)
    private String upiVpa;

    protected PayoutAccountEntity() {
        // JPA
    }

    PayoutAccountEntity(UUID driverAccountId) {
        this.driverAccountId = driverAccountId;
    }

    void replace(String holderName, String accountNumber, String ifsc, String upiVpa) {
        this.accountHolderName = holderName;
        this.accountNumber = accountNumber;
        this.ifsc = ifsc;
        this.upiVpa = upiVpa;
    }

    public UUID getDriverAccountId() {
        return driverAccountId;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getIfsc() {
        return ifsc;
    }

    public String getUpiVpa() {
        return upiVpa;
    }
}
