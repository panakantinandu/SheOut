package com.sheout.campaigns;

/** Where a campaign stands right now, derived - never stored - so it cannot disagree with its dates, budget and pause. */
public enum CampaignStatus {
    /** Applying now. */
    ACTIVE,
    /** Starts later. */
    SCHEDULED,
    /** Stopped by an operator. */
    PAUSED,
    /** Spent its budget cap and stopped by itself. */
    BUDGET_REACHED,
    /** Past its end date. */
    ENDED
}
