-- A live selfie, taken in the app's camera at submission, beside the ID.
--
-- selfie_document_key      the final, facing-the-camera frame.
-- liveness_frames_key      one image holding a frame per liveness prompt, in
--                          the order the prompts were asked.
-- selfie_prompts           those prompts, comma-separated, as the SERVER
--                          chose them - the console captions each frame from
--                          this, never from anything the phone sent.
-- selfie_captured_at       when the submission carrying the selfie arrived.
--
-- selfie_challenge_*       the prompts issued and not yet used. A
--                          submission must quote the nonce, within its
--                          expiry, once.
--
-- Nothing here scores or matches faces. A person compares the two images.
alter table verification_records
    add column selfie_document_key varchar(500),
    add column liveness_frames_key varchar(500),
    add column selfie_prompts varchar(200),
    add column selfie_captured_at timestamptz,
    add column selfie_challenge_nonce varchar(64),
    add column selfie_challenge_prompts varchar(200),
    add column selfie_challenge_expires_at timestamptz;
