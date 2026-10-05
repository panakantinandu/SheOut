-- A rider or partner can delete notifications from her inbox. Deleting hides
-- one from her and keeps the row: it is also the record of what SheOut sent
-- and how it was delivered (notification_deliveries), which a dispute or an
-- audit may need. Rows are erased with her account, as before.
alter table notification_log add column deleted_at timestamp;
