-- The customer About screen said every partner passes a "police-verification
-- review". That is not what SheOut can stand behind (docs/legal-audit.md D-1,
-- open item O-24), so the block now says what is checked: ID documents and the
-- driving licence by a person, and a live face check at the start of every
-- shift (ShiftCheckService).
--
-- Unlike V45, an operator's edit does not stand here: this removes a claim, so
-- any value that still mentions the police is replaced. The version moves on
-- so an editor holding the old copy is told it changed.
UPDATE content_blocks
SET value = 'A person checks every partner''s ID documents and driving licence before her first trip, and she passes a live face check at the start of every shift.',
    version = version + 1,
    updated_at = now()
WHERE content_key = 'about.feature.1.body'
  AND value ILIKE '%police%';
