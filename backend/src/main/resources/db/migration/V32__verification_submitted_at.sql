-- When the document was actually sent in.
--
-- The record already knew when it was reviewed, never when it arrived, so
-- "how long does a review take" could not be answered - and the wait is the
-- part somebody is actually experiencing. updated_at is not a substitute: it
-- moves every time anything on the row changes, including the review itself.
--
-- Backfilled from updated_at for rows that already hold a document. That is
-- an approximation and the only one available; from here it is recorded.
alter table verification_records
    add column document_submitted_at timestamptz;

update verification_records
set document_submitted_at = updated_at
where aadhaar_document_key is not null;
