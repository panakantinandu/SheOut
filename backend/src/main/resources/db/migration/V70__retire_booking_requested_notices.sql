-- "Booking requested - We're finding a partner near you" is no longer put in
-- a rider's inbox: the search is shown live on the booking screen, and the
-- line went on saying it after she cancelled or was matched. The ones
-- already there say something that stopped being true long ago. They were
-- inbox-only, so there are no deliveries to keep (and any would go with
-- them: notification_deliveries cascades).
delete from notification_log where type = 'BOOKING_REQUESTED';
