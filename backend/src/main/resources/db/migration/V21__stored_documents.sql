-- Uploaded files (profile photos, ID and vehicle documents) stored in
-- Postgres - see DatabaseDocumentStorage.
--
-- Until now production stored them on Render's container disk, which has no
-- web address a phone can load (so no photo ever displayed) and is wiped on
-- every restart or redeploy (so every upload was eventually lost while its
-- record still said it existed).
create table stored_documents (
    id            uuid primary key,
    storage_key   varchar(500)  not null unique,
    account_id    uuid          not null,
    content_type  varchar(100)  not null,
    size_bytes    integer       not null,
    content       bytea         not null,
    created_at    timestamp     not null,
    updated_at    timestamp     not null
);
create index idx_stored_documents_account on stored_documents (account_id);

-- Every existing profile photo pointed at a file on that disk, which is gone
-- by the time this runs (a deploy restarts the container). Keeping the keys
-- would tell the apps a photo exists that nobody can ever load; clearing
-- them sends each person to add her photo again, once.
update customer_profiles set profile_photo_key = null where profile_photo_key is not null;
update driver_profiles set profile_photo_key = null where profile_photo_key is not null;
