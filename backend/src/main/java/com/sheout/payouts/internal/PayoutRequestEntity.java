package com.sheout.payouts.internal;

import com.sheout.payouts.PayoutStatus;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_requests")
public class PayoutRequestEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID driverAccountId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status;

    /** Copied from her payout account when she asks - see V18. */
    @Column(length = 100)
    private String accountHolderName;

    @Column(length = 18)
    private String accountNumber;

    @Column(length = 11)
    private String ifsc;

    @Column(length = 100)
    private String upiVpa;

    private Instant paidAt;

    private UUID paidBy;

    @Column(length = 100)
    private String paymentReference;

    protected PayoutRequestEntity() {
        // JPA
    }

    PayoutRequestEntity(UUID driverAccountId, BigDecimal amount, PayoutAccountEntity destination) {
        this.driverAccountId = driverAccountId;
        this.amount = amount;
        this.status = PayoutStatus.PENDING;
        this.accountHolderName = destination.getAccountHolderName();
        this.accountNumber = destination.getAccountNumber();
        this.ifsc = destination.getIfsc();
        this.upiVpa = destination.getUpiVpa();
    }

    void markPaid(UUID adminAccountId, String reference) {
        this.status = PayoutStatus.PAID;
        this.paidAt = Instant.now();
        this.paidBy = adminAccountId;
        this.paymentReference = reference;
    }

    /**
     * Account deletion. The request is a financial record and stays; the
     * destination is reduced to what proves which account was paid without
     * keeping the account itself.
     */
    void anonymiseDestination() {
        this.accountHolderName = com.sheout.privacy.AccountDeletionRequested.DELETED_NAME;
        if (accountNumber != null && accountNumber.length() > 4) {
            accountNumber = "X".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
        }
        if (upiVpa != null) {
            int at = upiVpa.indexOf('@');
            upiVpa = at > 0 ? "xxxx" + upiVpa.substring(at) : "xxxx";
        }
    }

    public UUID getDriverAccountId() {
        return driverAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PayoutStatus getStatus() {
        return status;
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

    public Instant getPaidAt() {
        return paidAt;
    }

    public UUID getPaidBy() {
        return paidBy;
    }

    public String getPaymentReference() {
        return paymentReference;
    }
}
