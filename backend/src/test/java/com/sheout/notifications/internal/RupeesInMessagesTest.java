package com.sheout.notifications.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Amounts in pushes, texts and emails: whole rupees without ".00", paise only when there are some. */
class RupeesInMessagesTest {

    @Test
    void wholeAmountsHaveNoDecimals() {
        assertThat(NotificationEventListeners.rupees(new BigDecimal("120.00"))).isEqualTo("₹120");
        assertThat(NotificationEventListeners.rupees(new BigDecimal("3413"))).isEqualTo("₹3413");
        assertThat(NotificationEventListeners.rupees(new BigDecimal("0.00"))).isEqualTo("₹0");
        assertThat(NotificationEventListeners.rupees(null)).isEqualTo("₹0");
    }

    @Test
    void paiseAreShownExactly() {
        assertThat(NotificationEventListeners.rupees(new BigDecimal("1537.58"))).isEqualTo("₹1537.58");
        assertThat(NotificationEventListeners.rupees(new BigDecimal("100.5"))).isEqualTo("₹100.50");
    }
}
