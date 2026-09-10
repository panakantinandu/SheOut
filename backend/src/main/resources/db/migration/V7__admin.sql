-- Admin module. Adds the SOS resolve audit trail; the admin module itself
-- owns no domain tables (it composes other modules' interfaces), so there
-- is nothing else to create here.

-- SosStatus.RESOLVED already existed but nothing could reach it: the entity
-- had no transition and no record of who closed an alert. Both columns are
-- null for every existing (and every ACTIVE) alert, and are set together.
alter table sos_alert
    add column resolved_at timestamp,
    add column resolved_by uuid;
