package com.sheout.staff.internal;

import com.sheout.staff.Permission;
import com.sheout.staff.RefundLimits;
import com.sheout.staff.StaffRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** RefundLimits from configuration; see that interface for the figures and why. */
@Component
class ConfiguredRefundLimits implements RefundLimits {

    private final BigDecimal support;
    private final BigDecimal manager;
    private final BigDecimal finance;

    ConfiguredRefundLimits(@Value("${sheout.staff.refund-limit.support:200}") BigDecimal support,
                           @Value("${sheout.staff.refund-limit.manager:1000}") BigDecimal manager,
                           @Value("${sheout.staff.refund-limit.finance:200}") BigDecimal finance) {
        for (BigDecimal limit : new BigDecimal[]{support, manager, finance}) {
            if (limit.signum() < 0) {
                throw new IllegalStateException("A refund limit cannot be negative");
            }
        }
        this.support = support;
        this.manager = manager;
        this.finance = finance;
    }

    @Override
    public Optional<BigDecimal> limitFor(StaffRole role) {
        if (role == StaffRole.OWNER) {
            return Optional.empty();
        }
        if (!role.has(Permission.REFUNDS_ISSUE)) {
            return Optional.of(BigDecimal.ZERO);
        }
        return Optional.of(switch (role) {
            case MANAGER -> manager;
            case FINANCE -> finance;
            default -> support;
        });
    }
}
