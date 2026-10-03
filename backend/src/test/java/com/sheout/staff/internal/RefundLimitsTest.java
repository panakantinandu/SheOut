package com.sheout.staff.internal;

import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffRole;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The founder's limits (2026-10-03): support ₹200, manager ₹1,000, owners unlimited. */
class RefundLimitsTest {

    private final ConfiguredRefundLimits limits = new ConfiguredRefundLimits(
            new BigDecimal("200"), new BigDecimal("1000"), new BigDecimal("200"));

    private static StaffPrincipal as(StaffRole role) {
        return new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID(), role, UUID.randomUUID(), "Test", false);
    }

    @Test
    void eachRoleUpToItsOwnLimitAndNoFurther() {
        assertThat(limits.withinOwnLimit(as(StaffRole.SUPPORT_AGENT), new BigDecimal("200"))).isTrue();
        assertThat(limits.withinOwnLimit(as(StaffRole.SUPPORT_AGENT), new BigDecimal("200.01"))).isFalse();
        assertThat(limits.withinOwnLimit(as(StaffRole.MANAGER), new BigDecimal("1000"))).isTrue();
        assertThat(limits.withinOwnLimit(as(StaffRole.MANAGER), new BigDecimal("1001"))).isFalse();
        assertThat(limits.withinOwnLimit(as(StaffRole.FINANCE), new BigDecimal("201"))).isFalse();
        assertThat(limits.withinOwnLimit(as(StaffRole.OWNER), new BigDecimal("50000"))).isTrue();
        assertThat(limits.limitFor(StaffRole.OWNER)).isEmpty();
    }

    @Test
    void rolesThatCannotRefundHaveNothingToSpend() {
        for (StaffRole role : new StaffRole[]{StaffRole.VERIFICATION_AGENT, StaffRole.SAFETY_RESPONDER,
                StaffRole.MARKETPLACE_MODERATOR, StaffRole.AUDITOR}) {
            assertThat(limits.limitFor(role)).hasValue(BigDecimal.ZERO);
            assertThat(limits.withinOwnLimit(as(role), new BigDecimal("1"))).as(role.name()).isFalse();
        }
    }

    @Test
    void nothingAndNegativeAmountsAreNotRefunds() {
        assertThat(limits.withinOwnLimit(as(StaffRole.OWNER), BigDecimal.ZERO)).isFalse();
        assertThat(limits.withinOwnLimit(as(StaffRole.OWNER), new BigDecimal("-5"))).isFalse();
        assertThat(limits.withinOwnLimit(as(StaffRole.OWNER), null)).isFalse();
    }
}
