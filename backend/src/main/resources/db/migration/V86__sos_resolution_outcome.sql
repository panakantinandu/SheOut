-- Closing an SOS said only who closed it and when. What happened was nowhere:
-- a false alarm and a call to 112 looked the same afterwards. The console now
-- asks for the outcome (and a note) when an operator resolves an alert -
-- see docs/sos-runbook.md. Alerts resolved before this stay null.
ALTER TABLE sos_alert ADD COLUMN resolution_outcome VARCHAR(30);
ALTER TABLE sos_alert ADD COLUMN resolution_note VARCHAR(500);
