#!/usr/bin/env node
/**
 * Four test applicants through the real partner sign-up, each landing in the
 * state the verification checks should put her in:
 *
 *   clean           - nothing noticed: every check waits only for a person.
 *   expired licence - LICENCE fails automatically (DOCUMENT_EXPIRED), and
 *                     cannot be overridden: the fix is a renewed licence.
 *   name mismatch   - the name typed for the licence is not the profile's:
 *                     LICENCE needs a person (NAME_MISMATCH), never fails.
 *   duplicate plate - the RC's number is on another partner's account:
 *                     VEHICLE needs a person (DUPLICATE_NUMBER), never fails.
 *
 * A fifth account, "earlier partner", holds the plate the duplicate shares,
 * so the clean applicant stays clean.
 *
 * HOW IT IS KEPT OUT OF PRODUCTION
 *  1. It refuses any server but localhost and SheOut staging, by host name,
 *     before it sends anything.
 *  2. It signs in with fixed test codes, which the server only honours with
 *     DEV_MODE_ENABLED=true and DEV_OTP_TEST_PREFIX covering SEED_PREFIX.
 *     With dev mode off the first sign-in fails and nothing is created.
 *  3. Everything goes through the same endpoints the apps use, so it can do
 *     nothing a partner could not; it writes no rows of its own.
 *
 * Run (backend in dev mode, e.g. locally with DEV_OTP_TEST_PREFIX=+91999999):
 *   SEED_ADMIN_PHONE=<an ADMIN account's test number> node qa/seed-verification-applicants.mjs
 * SEED_ADMIN_PHONE is optional: with it, each applicant's checks are read
 * back through the console API and compared with what they should be.
 * Exit code 0 only when every applicant landed where she should.
 */
import zlib from 'node:zlib';

const API = process.env.SEED_API ?? 'http://localhost:8080';
const PREFIX = process.env.SEED_PREFIX ?? '+919999995';
const CODE = process.env.SEED_CODE ?? '123456';
const ADMIN_PHONE = process.env.SEED_ADMIN_PHONE ?? null;

const host = new URL(API).hostname;
if (!(host === 'localhost' || host === '127.0.0.1' || /^sheout-backend-staging[\w-]*\.onrender\.com$/.test(host))) {
  console.error(`Refusing to seed ${host}: test applicants go only to localhost or SheOut staging, never production.`);
  process.exit(2);
}

const today = new Date();
const isoDay = (d) => d.toISOString().slice(0, 10);
const plusDays = (n) => isoDay(new Date(today.getTime() + n * 864e5));
const year = today.getUTCFullYear();

// Example people: invented names and numbers, nobody real.
const SHARED_PLATE = 'TS09SQ4004';
const APPLICANTS = [
  {
    key: 'earlier', phone: `${PREFIX}000`, name: 'Revathi Naidu', dob: '1990-02-14', plate: SHARED_PLATE, holdsPlate: true,
    licence: { number: `TS09${year - 6}0040040`, validUntil: plusDays(2000), name: 'Revathi Naidu' },
  },
  {
    key: 'clean', phone: `${PREFIX}001`, name: 'Priya Sharma', dob: '1995-04-12', plate: 'TS09SQ1001',
    licence: { number: `TS09${year - 5}0010010`, validUntil: plusDays(2200), name: 'Priya Sharma' },
    expect: { LICENCE: ['NEEDS_REVIEW', []], VEHICLE: ['NEEDS_REVIEW', []], INSURANCE: ['NEEDS_REVIEW', []], IDENTITY: ['NEEDS_REVIEW', []] },
  },
  {
    key: 'expired', phone: `${PREFIX}002`, name: 'Kavya Reddy', dob: '1993-09-03', plate: 'TS09SQ2002',
    licence: { number: `TS09${year - 20}0020020`, validUntil: plusDays(-20), name: 'Kavya Reddy' },
    expect: { LICENCE: ['FAILED_AUTO', ['DOCUMENT_EXPIRED']], VEHICLE: ['NEEDS_REVIEW', []] },
    overall: 'FAILED_CHECK',
  },
  {
    key: 'name mismatch', phone: `${PREFIX}003`, name: 'Anitha Rao', dob: '1998-12-01', plate: 'TS09SQ3003',
    licence: { number: `TS09${year - 3}0030030`, validUntil: plusDays(2400), name: 'Sunitha Varma' },
    expect: { LICENCE: ['NEEDS_REVIEW', ['NAME_MISMATCH']], VEHICLE: ['NEEDS_REVIEW', []] },
  },
  {
    key: 'duplicate plate', phone: `${PREFIX}004`, name: 'Meena Kumari', dob: '1991-07-21', plate: SHARED_PLATE,
    licence: { number: `TS09${year - 4}0050050`, validUntil: plusDays(2100), name: 'Meena Kumari' },
    expect: { VEHICLE: ['NEEDS_REVIEW', ['DUPLICATE_NUMBER']], LICENCE: ['NEEDS_REVIEW', []] },
  },
];

// ---------------------------------------------------------------- http

async function call(method, path, token, body, form) {
  const res = await fetch(API + path, {
    method,
    headers: { ...(form ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = text; }
  if (!res.ok) throw new Error(`${method} ${path}: ${res.status} ${typeof json === 'string' ? json : JSON.stringify(json)}`);
  return json;
}

async function signIn(phone, role) {
  await call('POST', '/api/v1/auth/otp/request', null, { phoneNumber: phone, role });
  const out = await call('POST', '/api/v1/auth/otp/verify', null, { phoneNumber: phone, code: CODE, role });
  if (!out?.accessToken) throw new Error(`sign-in ${phone}: no token. Is the backend in dev mode with ${PREFIX} as a test prefix?`);
  return { token: out.accessToken, id: out.accountId };
}

// ---------------------------------------------------------------- images
// A real PNG, big enough for the server's checks (480x320, a readable size),
// drawn in stripes so it is not a blank page. No library needed.

const CRC = new Uint32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc32 = (buf) => {
  let c = 0xffffffff;
  for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
function png(width, height, seed) {
  const raw = Buffer.alloc((width * 3 + 1) * height);
  let x = seed >>> 0;
  for (let y = 0; y < height; y++) {
    const row = y * (width * 3 + 1);
    for (let i = 0; i < width; i++) {
      x = (Math.imul(x, 1103515245) + 12345) >>> 0;
      const band = ((i >> 4) + (y >> 4)) % 2 ? 210 : 70;
      const o = row + 1 + i * 3;
      raw[o] = (band + (x >>> 26)) & 255;
      raw[o + 1] = (band + (x >>> 27)) & 255;
      raw[o + 2] = band;
    }
  }
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body));
    return Buffer.concat([len, body, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // RGB
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const photo = (name, seed, w = 800, h = 520) => new File([png(w, h, seed)], name, { type: 'image/png' });

// ---------------------------------------------------------------- one applicant

async function apply(a, seed) {
  const { token, id } = await signIn(a.phone, 'DRIVER');
  // A profile photo first, as the app asks: the profile is refused without one.
  const face = new FormData();
  face.append('file', photo('profile.png', seed, 640, 640));
  await call('POST', '/api/v1/users/driver/me/photo', token, undefined, face);
  await call('PUT', '/api/v1/users/driver/me', token, {
    name: a.name, vehicleType: 'BIKE', vehicleRegistrationNumber: a.plate, dateOfBirth: a.dob,
  });
  const consent = await call('GET', '/api/v1/driver-verification/consent', token);
  if (!consent.current) await call('POST', '/api/v1/driver-verification/consent', token, { version: consent.currentVersion });

  const status = await call('GET', '/api/v1/driver-verification/me', token);
  if (status.genderVerificationStatus === 'PENDING' || status.genderVerificationStatus === 'REJECTED') {
    const challenge = await call('POST', '/api/v1/driver-verification/selfie-challenge', token);
    const form = new FormData();
    form.append('file', photo('id.png', seed + 1));
    form.append('selfie', photo('selfie.png', seed + 2, 640, 800));
    form.append('livenessFrames', photo('liveness.png', seed + 3, 1280, 480));
    form.append('selfieChallengeId', challenge.challengeId);
    await call('POST', '/api/v1/driver-verification/documents', token, undefined, form);
  }

  const upload = async (type, facts, s) => {
    const form = new FormData();
    form.append('file', photo(`${type.toLowerCase()}.png`, s));
    for (const [k, v] of Object.entries(facts)) if (v !== undefined && v !== null) form.append(k, String(v));
    return call('POST', `/api/v1/driver-verification/partner-documents/${type}`, token, undefined, form);
  };
  await upload('DRIVING_LICENCE', { documentNumber: a.licence.number, validUntil: a.licence.validUntil, nameOnDocument: a.licence.name }, seed + 4);
  await upload('VEHICLE_RC', { documentNumber: a.plate, validUntil: plusDays(3000), vehicleOwner: 'SELF' }, seed + 5);
  await upload('VEHICLE_INSURANCE', { documentNumber: `POL${year}${seed}77`, validUntil: plusDays(300), insuranceUseType: 'COMMERCIAL' }, seed + 6);
  await upload('POLICE_CERTIFICATE', { documentNumber: `TSPVC/${year}/${seed}`, issuedOn: plusDays(-30) }, seed + 7);
  return id;
}

// ---------------------------------------------------------------- run

const ids = {};
let seed = 100;
for (const a of APPLICANTS) {
  ids[a.key] = await apply(a, (seed += 10));
  console.log(`seeded ${a.key.padEnd(16)} ${a.phone.replace(/\d{3}$/, '***')}`);
}

if (!ADMIN_PHONE) {
  console.log('\nSEED_ADMIN_PHONE not set: open each applicant in the console to see her checks.');
  process.exit(0);
}

const admin = await signIn(ADMIN_PHONE, 'ADMIN');
let wrong = 0;
for (const a of APPLICANTS.filter((x) => x.expect)) {
  const checks = await call('GET', `/api/v1/admin/verification/${ids[a.key]}/checks`, admin.token);
  console.log(`\n${a.key}  overall: ${checks.overall}`);
  for (const c of checks.checks) {
    const findings = (c.findings ?? []).map((f) => f.code);
    const want = a.expect[c.type];
    const ok = !want || (c.status === want[0] && want[1].every((f) => findings.includes(f))
      && (want[1].length > 0 || findings.length === 0));
    if (!ok) wrong++;
    console.log(`  ${ok ? ' ' : 'X'} ${c.type.padEnd(13)} ${c.status.padEnd(19)} ${findings.join(', ') || '-'}${c.overridable ? '  (overridable)' : ''}`);
  }
  if (a.overall && checks.overall !== a.overall) {
    wrong++;
    console.log(`  X overall should be ${a.overall}`);
  }
}
console.log(wrong ? `\n${wrong} check(s) not where they should be.` : '\nEvery applicant landed where she should.');
process.exit(wrong ? 1 : 0);
