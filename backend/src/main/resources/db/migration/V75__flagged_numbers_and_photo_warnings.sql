-- Licence and vehicle numbers a person turned down for fraud (an altered
-- or fake document) or a confirmed mismatch (the government record shows
-- the licence or vehicle is someone else's). Never for an expired document,
-- a blurry photo or a typing mistake: those are fixed by sending it again,
-- and remembering them would follow an honest applicant around.
--
-- When the same number comes in again, on any account, its check fails
-- automatically (PREVIOUSLY_REJECTED). A person still decides: sends it
-- back, or overrides with a written note and a second person's sign-off.
--
-- Kept when the account that sent it is deleted. Otherwise deleting an
-- account and signing up again would be all it took to clear the flag. The
-- number is kept with the decision; nothing else about her is.
create table flagged_document_numbers (
    id              uuid primary key,
    document_type   varchar(40)  not null,
    document_number varchar(64)  not null,
    reason_code     varchar(40)  not null,
    account_id      uuid         not null,
    document_id     uuid,
    flagged_by      uuid         not null,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null
);

create index idx_flagged_document_numbers on flagged_document_numbers (document_type, document_number);

-- What her phone said about her ID photo and selfie when she chose to send
-- them anyway (BLURRY, GLARE, DARK, NO_FACE), so the reviewer is asked to
-- look. Null when the phone saw nothing wrong, or did not check.
alter table verification_records add column id_photo_warnings varchar(100);
alter table verification_records add column selfie_warnings varchar(100);

-- Duplicate number lookups: the same licence or plate on another account.
create index idx_partner_documents_number
    on partner_documents (type, document_number) where superseded_at is null and document_number is not null;
