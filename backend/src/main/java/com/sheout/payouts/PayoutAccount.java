package com.sheout.payouts;

import com.sheout.sharedkernel.privacy.Pii;

import java.time.Instant;

/**
 * Where a partner is paid: a bank account (all three bank fields), a UPI
 * VPA, or both. updatedAt is null on a value being saved.
 */
public record PayoutAccount(
        String accountHolderName,
        @Pii(Pii.Kind.BANK) String accountNumber,
        String ifsc,
        @Pii(Pii.Kind.BANK) String upiVpa,
        Instant updatedAt
) {

    public boolean hasBankAccount() {
        return notBlank(accountHolderName) && notBlank(accountNumber) && notBlank(ifsc);
    }

    public boolean hasUpi() {
        return notBlank(upiVpa);
    }

    /** "XXXXXX1234" - for anything the partner sees on a shared phone screen; operators see the full number. */
    public String maskedAccountNumber() {
        if (accountNumber == null || accountNumber.length() < 4) return accountNumber;
        return "X".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
