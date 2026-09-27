package com.sheout.campaigns;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A referred friend took her first paid trip and the referral was settled.
 * The two amounts are what each side was actually given - zero when that
 * side's campaign was paused, out of budget, or the referrer was at her
 * limit. A partner's money also arrives as a DriverIncentiveAwarded.
 */
public class ReferralCompleted extends DomainEvent {

    private final UUID referralId;
    private final UUID referrerAccountId;
    private final UUID refereeAccountId;
    private final AccountRole role;
    private final BigDecimal referrerReward;
    private final BigDecimal refereeReward;

    public ReferralCompleted(UUID referralId, UUID referrerAccountId, UUID refereeAccountId, AccountRole role,
                             BigDecimal referrerReward, BigDecimal refereeReward) {
        this.referralId = referralId;
        this.referrerAccountId = referrerAccountId;
        this.refereeAccountId = refereeAccountId;
        this.role = role;
        this.referrerReward = referrerReward;
        this.refereeReward = refereeReward;
    }

    public UUID referralId() { return referralId; }
    public UUID referrerAccountId() { return referrerAccountId; }
    public UUID refereeAccountId() { return refereeAccountId; }
    public AccountRole role() { return role; }
    public BigDecimal referrerReward() { return referrerReward; }
    public BigDecimal refereeReward() { return refereeReward; }
}
