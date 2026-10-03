-- verification_records.rc_document_key was copied into partner_documents by
-- V55 and kept only for the release that was still serving while V55's
-- release started. That release is gone, so the column goes.
--
-- An older backend could still have written an RC here after V55 ran (a
-- partner submitting her ID through it during the overlap). Any key that
-- never reached partner_documents is copied first, as V55 did, so no photo
-- is lost. If she already has a current RC on her checklist, that one stays
-- current and this one is filed as history (superseded) - still hers, still
-- erased with her account, but not put in front of an operator again.
insert into partner_documents (id, account_id, type, document_key, status, source, metadata_json,
                               submitted_at, superseded_at, created_at, updated_at)
select gen_random_uuid(), v.account_id, 'VEHICLE_RC', v.rc_document_key, 'UNDER_REVIEW', 'PARTNER_UPLOAD',
       '{"migratedFrom":"verification_records.rc_document_key"}',
       coalesce(v.document_submitted_at, v.updated_at),
       case when exists (select 1 from partner_documents c
                         where c.account_id = v.account_id and c.type = 'VEHICLE_RC' and c.superseded_at is null)
            then now() end,
       now(), now()
from verification_records v
where v.rc_document_key is not null
  and not exists (select 1 from partner_documents d
                  where d.account_id = v.account_id and d.document_key = v.rc_document_key);

alter table verification_records drop column rc_document_key;
