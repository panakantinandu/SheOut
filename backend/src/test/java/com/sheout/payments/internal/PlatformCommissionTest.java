package com.sheout.payments.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The partner keeps at least 80%: a commission above the ceiling stops the server starting, and says why. */
class PlatformCommissionTest {

    @Test
    void eighteenPercentLeavesHerEightyTwo() {
        PlatformCommission c = new PlatformCommission(new BigDecimal("18.00"), new BigDecimal("20.00"));

        assertThat(c.payoutFrom(new BigDecimal("100.00"))).isEqualByComparingTo("82.00");
    }

    @Test
    void exactlyTheCeilingIsAllowed() {
        PlatformCommission c = new PlatformCommission(new BigDecimal("20.00"), new BigDecimal("20.00"));

        assertThat(c.payoutFrom(new BigDecimal("100.00"))).isEqualByComparingTo("80.00");
    }

    @Test
    void aboveTheCeilingRefusesToStartNamingTheEightyPercentRule() {
        assertThatThrownBy(() -> new PlatformCommission(new BigDecimal("22.50"), new BigDecimal("20.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLATFORM_COMMISSION_MAX_PERCENT")
                .hasMessageContaining("at least 80% of the fare");
    }

    @Test
    void theCeilingItselfIsConfiguration() {
        PlatformCommission c = new PlatformCommission(new BigDecimal("22.50"), new BigDecimal("25.00"));

        assertThat(c.percent()).isEqualByComparingTo("22.50");
    }
}
