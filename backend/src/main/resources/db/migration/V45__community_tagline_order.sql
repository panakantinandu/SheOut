-- The rider Home's community tagline reads Safe, Together, Empowered: the
-- promise first, then the company, then what it leads to. Only the seeded
-- value is changed - if an operator has edited it (updated_by set), hers
-- stands. The version moves on so an editor holding the old copy is told
-- it changed rather than overwriting it.
UPDATE content_blocks
SET value = 'Safe · Together · Empowered',
    version = version + 1,
    updated_at = now()
WHERE content_key = 'home.community.subtitle'
  AND updated_by IS NULL
  AND value = 'Safe · Empowered · Together';
