-- SheOut cannot text emergency contacts yet: no SMS provider is live until the
-- DLT templates are approved, so every text to a contact fails and the SOS
-- screen offers a one-tap button to send it from the customer's own phone.
-- The About screen and the FAQ said contacts "are sent an SMS". These blocks
-- now say what happens (docs/claim-scan-2026-10-15.md, section 1). When the
-- SMS provider goes live, change them back in the console or a migration.
--
-- Like V84, an operator's edit does not stand: any value still promising an
-- SMS from SheOut is replaced, and the version moves on.
UPDATE content_blocks
SET value = 'Save contacts on your account. When you raise an SOS, SheOut''s team gets your location at once, and the SOS screen lets you text your contacts from your own phone in one tap.',
    version = version + 1,
    updated_at = now()
WHERE content_key = 'about.feature.2.body'
  AND value ILIKE '%by SMS%';

UPDATE content_blocks
SET value = 'SheOut''s operators get the alert with your live location at once. SheOut cannot text your emergency contacts yet - its SMS service is waiting for approval - so the SOS screen gives you a one-tap button to text them from your own phone, with your location filled in.',
    version = version + 1,
    updated_at = now()
WHERE content_key = 'faq.customer.4.answer'
  AND value ILIKE '%sent an SMS%';
