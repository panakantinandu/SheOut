#!/usr/bin/env node
// Legal promises that live in two places must say the same thing.
//
//   node scripts/check-legal-sync.mjs                 source checks only
//   node scripts/check-legal-sync.mjs --bundles a b   also scan built dist folders
//
// Source checks (frontend legal content vs the backend that enforces it):
//   - the Terms version the apps show = the one the server records;
//   - the partner verification consent version = the server's;
//   - the prohibited parcel items and their version = ParcelRules;
//   - the Grievance Officer email = the server's default;
//   - the refund-to-original-method timeline = the server's.
// Bundle checks: no claim SheOut cannot stand behind, and no placeholder
// text, in what a browser downloads.
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const root = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const read = (p) => readFileSync(join(root, p), 'utf8');
const failures = [];
const expectEqual = (what, a, b) => {
  if (JSON.stringify(a) !== JSON.stringify(b)) failures.push(`${what}: ${JSON.stringify(a)} != ${JSON.stringify(b)}`);
};
const one = (text, re, what) => {
  const m = text.match(re);
  if (!m) failures.push(`could not find ${what}`);
  return m?.[1];
};

const content = read('frontend/design-system/src/legal/content.ts');
const entity = JSON.parse(read('frontend/design-system/src/legal/legal-entity.config.json'));
const yml = read('backend/src/main/resources/application.yml');
const parcelRules = read('backend/src/main/java/com/sheout/booking/internal/ParcelRules.java');
const policeRules = read('backend/src/main/java/com/sheout/driververification/internal/PoliceVerificationRules.java');

expectEqual(
  'Terms version (content.ts LEGAL_VERSION vs application.yml sheout.legal.terms-version)',
  one(content, /export const LEGAL_VERSION = '([^']+)'/, 'LEGAL_VERSION'),
  one(yml, /terms-version: \$\{LEGAL_TERMS_VERSION:([^}]+)\}/, 'terms-version')
);
expectEqual(
  'Verification consent version (content.ts vs PoliceVerificationRules)',
  one(content, /export const VERIFICATION_CONSENT_VERSION = '([^']+)'/, 'VERIFICATION_CONSENT_VERSION'),
  one(policeRules, /VERIFICATION_CONSENT_VERSION:([^}]+)\}/, 'consent version default')
);
expectEqual(
  'Parcel prohibited items version (content.ts vs application.yml)',
  one(content, /export const PARCEL_PROHIBITED_ITEMS_VERSION = '([^']+)'/, 'PARCEL_PROHIBITED_ITEMS_VERSION'),
  one(yml, /prohibited-items-version: \$\{PARCEL_PROHIBITED_ITEMS_VERSION:([^}]+)\}/, 'prohibited-items-version')
);
const frontItems = [...(one(content, /PARCEL_PROHIBITED_ITEMS: Record<string, string> = \{([\s\S]*?)\};/, 'items') ?? '').matchAll(/^\s*([A-Z_]+):/gm)].map((m) => m[1]);
const backItems = [...(one(parcelRules, /PROHIBITED_ITEMS = List\.of\(([\s\S]*?)\);/, 'PROHIBITED_ITEMS') ?? '').matchAll(/"([A-Z_]+)"/g)].map((m) => m[1]);
expectEqual('Parcel prohibited items (content.ts vs ParcelRules)', frontItems, backItems);
expectEqual(
  'Grievance Officer email (legal-entity.config.json vs application.yml)',
  entity.grievanceOfficer.email,
  one(yml, /grievance-officer-email: \$\{GRIEVANCE_OFFICER_EMAIL:([^}]*)\}/, 'grievance-officer-email')
);

expectEqual(
  'Refund to original method, working days (legal-entity.config.json vs application.yml)',
  String(entity.refundToSourceWorkingDays),
  one(yml, /to-source-working-days: \$\{REFUND_TO_SOURCE_WORKING_DAYS:([^}]+)\}/, 'to-source-working-days')
);

// Claims removed in docs/legal-audit.md (D-1) and placeholder content that
// must never ship. Case-insensitive; matched in built JS, CSS and HTML.
const BANNED = [
  /police[- ]verified/i,
  /background[- ]verified/i,
  /100% safe/i,
  /lorem ipsum/i,
  /trusted by \d/i,
];
const bundleArgs = process.argv.includes('--bundles') ? process.argv.slice(process.argv.indexOf('--bundles') + 1) : [];
const files = (dir) =>
  readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    return statSync(p).isDirectory() ? files(p) : /\.(js|css|html)$/.test(name) ? [p] : [];
  });
for (const dir of bundleArgs) {
  for (const file of files(join(root, dir))) {
    const text = readFileSync(file, 'utf8');
    for (const re of BANNED) if (re.test(text)) failures.push(`${file}: contains ${re}`);
  }
}

if (failures.length) {
  console.error('Legal sync check failed:\n- ' + failures.join('\n- '));
  process.exit(1);
}
console.log(`Legal sync check passed${bundleArgs.length ? ` (${bundleArgs.length} bundles scanned)` : ''}.`);
