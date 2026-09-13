-- What the rider pays, and what the partner actually receives.
--
-- Two separate, visible numbers. The commission is NOT baked into a higher
-- customer-facing price and then quietly subtracted: the rider pays the
-- fare she was quoted, and the platform's cut comes out of that, recorded.
-- Hiding the margin inside the fare would make the platform's revenue
-- unmeasurable from its own database, and would make a driver-facing payout
-- breakdown impossible to build honestly later.
--
-- Both are stored rather than one being derived at read time. The rate will
-- change - it is configuration, and the first driver conversation about
-- earnings may move it - and a payout recomputed from today's rate would
-- silently rewrite what somebody was paid last month.
ALTER TABLE payments
    ADD COLUMN driver_payout      NUMERIC(10, 2),
    ADD COLUMN commission_percent NUMERIC(5, 2);

COMMENT ON COLUMN payments.driver_payout IS
    'What the partner receives: amount x (1 - commission_percent/100). NULL until the payment is captured, because nothing is owed to anyone before then.';
COMMENT ON COLUMN payments.commission_percent IS
    'The commission rate in force at the moment of capture, frozen onto the row. Stored so a later rate change cannot rewrite what a partner was already paid.';
