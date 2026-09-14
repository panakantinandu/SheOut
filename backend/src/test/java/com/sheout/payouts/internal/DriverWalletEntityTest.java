package com.sheout.payouts.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the wallet arithmetic.
 * <p>
 * The cash case is the one worth guarding. On a cash trip the partner already
 * holds the whole fare; crediting her share as if the rider had paid online
 * would pay her twice. What she owes the platform is the commission, so the
 * trip must leave her available balance lower by exactly that.
 */
class DriverWalletEntityTest {

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    @Test
    @DisplayName("an online trip makes the partner's share available")
    void onlineTripCreditsShare() {
        DriverWalletEntity wallet = new DriverWalletEntity();
        wallet.creditEarning(money("410.00"));

        assertEquals(money("410.00"), wallet.available());
    }

    @Test
    @DisplayName("a cash trip nets to minus the commission, not plus the share")
    void cashTripNetsToMinusCommission() {
        DriverWalletEntity wallet = new DriverWalletEntity();
        // 100 fare, 18% commission: she keeps 82 of the 100 in her hand.
        wallet.creditEarning(money("82.00"));
        wallet.recordCashCollected(money("100.00"));

        assertEquals(money("-18.00"), wallet.available());
        // A later online trip is settled against what she owes.
        wallet.creditEarning(money("410.00"));
        assertEquals(money("392.00"), wallet.available());
    }

    @Test
    @DisplayName("a request leaves the balance at once and settling does not take it twice")
    void payoutHoldAndSettle() {
        DriverWalletEntity wallet = new DriverWalletEntity();
        wallet.creditEarning(money("500.00"));

        wallet.holdForPayout(money("300.00"));
        assertEquals(money("200.00"), wallet.available());
        assertEquals(money("300.00"), wallet.getPendingPayouts());

        wallet.settlePayout(money("300.00"));
        assertEquals(money("200.00"), wallet.available());
        assertEquals(money("0.00"), wallet.getPendingPayouts());
        assertEquals(money("300.00"), wallet.getTotalPaidOut());
    }
}
