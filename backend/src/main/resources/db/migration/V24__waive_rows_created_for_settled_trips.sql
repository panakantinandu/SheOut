-- A payment row created after V23 for a trip V23 had already settled - a
-- trip that ended with no payment row at all, whose row was then recreated as
-- PENDING the first time anyone looked at it. The trip is closed; its row is
-- recorded the way V23 recorded the others, so the rider is not offered a
-- "Pay" button for it.
UPDATE payments p
   SET status = 'WAIVED',
       failure_reason = 'Trip ended before payment was required to close it'
  FROM bookings b
 WHERE b.id = p.booking_id
   AND b.payment_settled_at IS NOT NULL
   AND p.status IN ('PENDING', 'FAILED');
