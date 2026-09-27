package com.sheout.campaigns;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A new account signed up with somebody's referral code. Nothing is owed
 * yet - the reward comes with her first paid trip - but the one who shared
 * the code likes to know it worked.
 * <p>
 * referrerReward is what she stands to get when this friend pays for a first
 * trip, as things are now - null when her side is paused, out of budget or
 * she is at her limit, so nothing is promised that would not be paid.
 */
public class ReferralJoined extends DomainEvent {

    private final UUID referralId;
    private final UUID referrerAccountId;
    private final UUID refereeAccountId;
    private final AccountRole role;
    private final BigDecimal referrerReward;

    public ReferralJoined(UUID referralId, UUID referrerAccountId, UUID refereeAccountId, AccountRole role,
                          BigDecimal referrerReward) {
        this.referralId = referralId;
        this.referrerAccountId = referrerAccountId;
        this.refereeAccountId = refereeAccountId;
        this.role = role;
        this.referrerReward = referrerReward;
    }

    public UUID referralId() { return referralId; }
    public UUID referrerAccountId() { return referrerAccountId; }
    public UUID refereeAccountId() { return refereeAccountId; }
    public AccountRole role() { return role; }
    public BigDecimal referrerReward() { return referrerReward; }
}
