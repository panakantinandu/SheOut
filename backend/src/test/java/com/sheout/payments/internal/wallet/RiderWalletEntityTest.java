package com.sheout.payments.internal.wallet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The balance rules a rider's money depends on. The database refuses a
 * negative balance too (V23); these pin the same rule where it is first
 * applied, so a bug shows up as a failed test rather than as a constraint
 * violation in the middle of somebody's payment.
 */
class RiderWalletEntityTest {

    private static RiderWalletEntity walletWith(String amount) {
        RiderWalletEntity wallet = new RiderWalletEntity(UUID.randomUUID());
        wallet.credit(new BigDecimal(amount));
        return wallet;
    }

    @Test
    @DisplayName("a fare comes off the balance exactly")
    void debitSubtracts() {
        RiderWalletEntity wallet = walletWith("500.00");
        wallet.debit(new BigDecimal("123.45"));
        assertEquals(new BigDecimal("376.55"), wallet.getBalance());
    }

    @Test
    @DisplayName("a fare equal to the whole balance empties it")
    void debitCanEmptyTheWallet() {
        RiderWalletEntity wallet = walletWith("100.00");
        wallet.debit(new BigDecimal("100.00"));
        assertEquals(0, wallet.getBalance().signum());
    }

    @Test
    @DisplayName("a fare larger than the balance is refused and the balance is untouched")
    void debitNeverOverdraws() {
        RiderWalletEntity wallet = walletWith("99.99");
        assertThrows(IllegalStateException.class, () -> wallet.debit(new BigDecimal("100.00")));
        assertEquals(new BigDecimal("99.99"), wallet.getBalance());
    }

    @Test
    @DisplayName("zero and negative movements are refused - a negative debit would be a credit in disguise")
    void movementsMustBePositive() {
        RiderWalletEntity wallet = walletWith("50.00");
        assertThrows(IllegalArgumentException.class, () -> wallet.debit(new BigDecimal("-10.00")));
        assertThrows(IllegalArgumentException.class, () -> wallet.credit(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> wallet.credit(null));
        assertEquals(new BigDecimal("50.00"), wallet.getBalance());
    }
}
