-- The server's own face comparison for a start-of-shift selfie (AWS
-- Rekognition, 0-100), beside the distance the phone reported. Null when the
-- server did not compare - switched off, over its monthly cap, or AWS down.
alter table shift_checks add column server_similarity double precision;
