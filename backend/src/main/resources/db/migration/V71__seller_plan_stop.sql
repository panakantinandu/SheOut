-- A seller can stop her monthly plan. Nothing is charged automatically, so
-- stopping takes no money back and none away: her shop stays listed until
-- the month she has (free or paid) ends, then is hidden at once - no grace
-- days, no reminders - with nothing deleted. She can undo it before then,
-- and paying again later restarts it. See the marketplace's SellerPlan.
alter table seller_profiles
    -- When she stopped it; null while her plan runs.
    add column plan_stopped_at  timestamptz,
    -- Why, if she said (PlanStopReason).
    add column plan_stop_reason varchar(30);
