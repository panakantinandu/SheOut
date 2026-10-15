#!/usr/bin/env node
// The public site's words and pictures, checked on every push.
//   node scripts/check-marketing-assets.mjs            fail on invented ratings, counts or placeholder text,
//                                                      on a picture that is missing or that nothing uses
//   node scripts/check-marketing-assets.mjs --strict   also fail while any screenshot is still an old capture
//                                                      (the launch gate runs this; CI only lists them)
// The screenshots themselves are listed in frontend/marketing-site/screens.json:
// when each was captured, from what, and whether it is still an old capture.
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, relative } from 'node:path';

const root = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const site = join(root, 'frontend/marketing-site');
const strict = process.argv.includes('--strict');
const failures = [];
const warnings = [];

const walk = (dir, ext) =>
  readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    return statSync(p).isDirectory() ? walk(p, ext) : ext.test(name) ? [p] : [];
  });
const sources = [...walk(join(site, 'src'), /\.(ts|tsx|css)$/), join(site, 'index.html')];
const text = Object.fromEntries(sources.map((f) => [f, readFileSync(f, 'utf8')]));

// No rating, review count or user count: SheOut has not launched, so any such number is invented.
// Placeholder text never ships. (TODO_LEGAL, the legal-entity marker, is handled by check-legal-sync.)
const BANNED = [
  [/\b[1-5]\.\d\s*(★|\/\s*5|stars?\b|rating)/i, 'a star rating'],
  [/★\s*[1-5](\.\d)?/, 'a star rating'],
  [/\(\s*\d[\d,]*\s*(ratings?|reviews?)\s*\)/i, 'a ratings count'],
  [/\b\d[\d,.]*\s*(k|lakh|L)?\+?\s*(happy\s+)?(riders|customers|users|downloads|reviews|rides (completed|taken|booked))\b/i, 'a user or ride count'],
  [/\brated\s+[1-5](\.\d)?\b/i, 'a rating'],
  [/lorem ipsum/i, 'placeholder text'],
  [/\b(TODO|TBD|FIXME)\b/, 'a placeholder note'],
  [/\bcoming soon\b/i, 'a "coming soon" placeholder'],
];
for (const [file, body] of Object.entries(text)) {
  // Comments may explain why a thing is not shown; only shipped strings count.
  const shipped = body.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1').replace(/<!--[\s\S]*?-->/g, '');
  for (const [re, what] of BANNED) {
    const m = shipped.match(re);
    if (m) failures.push(`${relative(root, file)}: ${what}: "${m[0]}"`);
  }
}

// Every picture the site names exists, and every picture it ships is named somewhere.
const pub = join(site, 'public');
const all = Object.values(text).join('\n');
const named = new Set();
for (const m of all.matchAll(/\/(screens|media)\/[\w.-]+\.(webp|png|jpg|mp4|webm)/g)) named.add(m[0].slice(1));
for (const m of all.matchAll(/screen[=:]\s*['"{]?\s*['"]([\w-]+)['"]/g)) named.add(`screens/${m[1]}.webp`);
for (const p of named) if (!existsSync(join(pub, p))) failures.push(`public/${p} is used by the site but missing`);
for (const dir of ['screens', 'media']) {
  for (const f of readdirSync(join(pub, dir))) {
    if (!named.has(`${dir}/${f}`)) failures.push(`public/${dir}/${f} ships but nothing on the site uses it`);
  }
}

// The screenshot manifest covers every screenshot.
const manifest = JSON.parse(readFileSync(join(site, 'screens.json'), 'utf8'));
for (const f of readdirSync(join(pub, 'screens'))) {
  const name = f.replace(/\.webp$/, '');
  const entry = manifest.screens[name];
  if (!entry) failures.push(`public/screens/${f} is not listed in screens.json`);
  else if (entry.oldCapture) (strict ? failures : warnings).push(`screens/${f}: old capture - ${entry.oldCapture}`);
}
for (const name of Object.keys(manifest.screens)) {
  if (!existsSync(join(pub, 'screens', `${name}.webp`))) failures.push(`screens.json lists ${name} but public/screens/${name}.webp is missing`);
}
for (const [file, entry] of Object.entries(manifest.other ?? {})) {
  if (entry.oldCapture) (strict ? failures : warnings).push(`${file}: old capture - ${entry.oldCapture}`);
}

if (warnings.length) console.warn(`Still to re-shoot (${warnings.length}):\n- ` + warnings.join('\n- '));
if (failures.length) {
  console.error('Marketing assets check failed:\n- ' + failures.join('\n- '));
  process.exit(1);
}
console.log(`Marketing assets check passed${strict ? ' (strict)' : ''}: ${sources.length} files, ${named.size} pictures.`);
