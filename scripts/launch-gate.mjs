#!/usr/bin/env node
// Launch gate: is SheOut ready for real customers? Prints PASS / FAIL / MANUAL for every
// item in docs/launch-gate.md and exits 1 while anything FAILs.
//
//   node scripts/launch-gate.mjs                       the repository only (files, built bundles, check scripts)
//   node scripts/launch-gate.mjs --live                also read the live sites, the public content API and DNS
//   node scripts/launch-gate.mjs --env-file prod.env   also check production switches from a KEY=VALUE file the
//                                                      owner copies from Render (never commit it; only the keys
//                                                      below are read, and no value is printed)
//   node scripts/launch-gate.mjs --offline             skip npm audit (it asks the npm registry)
//
// READ-ONLY. It reads files, runs the repo's own check scripts and `npm audit` (never `--fix`),
// and with --live makes GET requests and DNS lookups. It never writes a file, calls an admin
// API, signs in, or changes a setting anywhere.
import { readFileSync, existsSync, statSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { resolveMx } from 'node:dns/promises';

const root = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const args = process.argv.slice(2);
const LIVE = args.includes('--live');
const OFFLINE = args.includes('--offline');
const envFile = args.includes('--env-file') ? args[args.indexOf('--env-file') + 1] : null;
const SITES = {
  'customer app': 'https://app.sheoutride.com',
  'partner app': 'https://partner.sheoutride.com',
  'public site': 'https://www.sheoutride.com',
};
const API = process.env.LAUNCH_GATE_API ?? 'https://sheout-backend.onrender.com';

const results = [];
const add = (area, item, status, detail = '') => results.push({ area, item, status, detail });
const read = (p) => readFileSync(join(root, p), 'utf8');
const exists = (p) => existsSync(join(root, p));
const run = (cmd, argv) => spawnSync(cmd, argv, { cwd: root, encoding: 'utf8', shell: process.platform === 'win32', timeout: 300_000 });

// Claims removed after docs/legal-audit.md D-1, plus insurance wording that must be backed by a live policy.
const CLAIMS = [
  [/police[- ]verified/i, '"police verified"'],
  [/police[- ]verification review/i, '"police-verification review"'],
  [/background[- ](verified|checked)/i, '"background verified/checked"'],
  [/100% safe/i, '"100% safe"'],
  [/trusted by \d/i, '"trusted by <number>"'],
];

// ---------------------------------------------------------------- A. Business details and legal pages
{
  const entity = JSON.parse(read('frontend/design-system/src/legal/legal-entity.config.json'));
  const todo = [];
  const walk = (o, path) => {
    for (const [k, v] of Object.entries(o)) {
      if (k.startsWith('_')) continue;
      if (typeof v === 'string' && v.includes('TODO_FROM_OWNER')) todo.push(path + k);
      else if (v && typeof v === 'object' && !Array.isArray(v)) walk(v, `${path}${k}.`);
    }
  };
  walk(entity, '');
  add('A. Legal', 'Business details complete (legal-entity.config.json)', todo.length ? 'FAIL' : 'PASS',
    todo.length ? `still TODO_FROM_OWNER: ${todo.join(', ')} (O-2, O-3, O-4)` : 'no placeholders');
  add('A. Legal', 'LEGAL_STRICT_BUILD=true on all three Vercel projects', 'MANUAL',
    'Vercel > each project > Settings > Environment Variables (Production). With it on, a build with any placeholder fails');

  const content = read('frontend/design-system/src/legal/content.ts');
  const drafts = (content.match(/DRAFT, pending legal review/g) ?? []).length;
  const lawyer = (content.match(/TODO_LAWYER\b(?!\s*=)/g) ?? []).length;
  add('A. Legal', 'Legal pages final (no DRAFT or [to be reviewed by lawyer] marks)', drafts || lawyer ? 'FAIL' : 'PASS',
    `${drafts} DRAFT marks, ${lawyer} lawyer-review marks in content.ts`);

  // docs/legal-open-items.md, section "D. Lawyer sign-off": one row per document.
  const items = read('docs/legal-open-items.md');
  const section = items.split(/^## /m).find((s) => s.startsWith('D. Lawyer sign-off')) ?? '';
  const rows = section.split('\n').filter((l) => /^\|/.test(l) && !/^\|\s*-/.test(l)).slice(1)
    .map((l) => l.split('|').slice(1, -1).map((c) => c.trim()));
  if (!rows.length) add('A. Legal', 'Lawyer sign-off recorded', 'FAIL', 'no "D. Lawyer sign-off" table in docs/legal-open-items.md');
  for (const [doc, english, translations] of rows) {
    const ok = /^approved \d{4}-\d{2}-\d{2}/i.test(english ?? '');
    const name = doc.replace(/\s*\(.*\)\s*$/, '');
    add('A. Legal', `Lawyer sign-off: ${name}`, ok ? 'PASS' : 'FAIL', ok ? english : `English: ${english || 'not approved'}`);
    if (ok && translations !== undefined && !/^approved \d{4}-\d{2}-\d{2}/i.test(translations)) {
      add('A. Legal', `Hindi and Telugu: ${name}`, 'FAIL', `translations: ${translations || 'not done'}`);
    }
  }
}

// ---------------------------------------------------------------- B. What the apps and site say
{
  const files = [
    ...['customer-app', 'driver-app'].flatMap((a) => ['en', 'hi', 'te'].map((l) => `frontend/${a}/src/i18n/${l}.json`)),
    'frontend/design-system/src/legal/content.ts',
    'frontend/marketing-site/src/content.ts',
    'frontend/marketing-site/index.html',
  ].filter(exists);
  const hits = [];
  for (const f of files) {
    const t = read(f);
    for (const [re, what] of CLAIMS) {
      if (!re.test(t)) continue;
      const key = t.match(new RegExp(`"([\\w.]+)":\\s*"[^"]*${re.source}`, re.flags))?.[1];
      hits.push(`${f}${key ? ` (${key})` : ''}: ${what}`);
    }
  }
  // Content blocks seeded by migrations (the English the apps show first; editable in the console).
  const mig = 'backend/src/main/resources/db/migration';
  for (const f of readdirSync(join(root, mig)).filter((n) => n.endsWith('.sql'))) {
    const t = read(`${mig}/${f}`);
    for (const [re, what] of CLAIMS) {
      for (const line of t.split('\n')) {
        if (re.test(line) && /content_blocks|'[a-z]+\.[a-z0-9.]+'/.test(line)) {
          const key = line.match(/'([a-z]+\.[a-z0-9.]+)'/)?.[1] ?? '?';
          hits.push(`${mig}/${f} (content block ${key}): ${what} - unless a later migration or a console edit changed it (--live checks)`);
        }
      }
    }
  }
  add('B. Claims', 'No removed claims in app, site or legal text (D-1)', hits.length ? 'FAIL' : 'PASS', hits.join('\n') || `${files.length} files and the migrations scanned`);

  const sync = run('node', ['scripts/check-legal-sync.mjs']);
  add('B. Claims', 'Legal pages agree with the server (check-legal-sync)', sync.status === 0 ? 'PASS' : 'FAIL', (sync.stdout + sync.stderr).trim().split('\n').slice(-4).join('\n'));

  const dists = ['frontend/customer-app/dist', 'frontend/driver-app/dist', 'frontend/marketing-site/dist'].filter(exists);
  if (dists.length) {
    const b = run('node', ['scripts/check-legal-sync.mjs', '--bundles', ...dists]);
    const age = Math.round((Date.now() - Math.min(...dists.map((d) => statSync(join(root, d)).mtimeMs))) / 3_600_000);
    add('B. Claims', 'Built bundles free of removed claims', b.status === 0 ? 'PASS' : 'FAIL', `${dists.length} local builds, oldest ${age} h old. ${(b.stdout + b.stderr).trim().split('\n').slice(-3).join(' ')}`);
  } else add('B. Claims', 'Built bundles free of removed claims', 'MANUAL', 'no local builds; run the three builds first (CI runs this on every push)');

  if (exists('scripts/check-marketing-assets.mjs')) {
    const m = run('node', ['scripts/check-marketing-assets.mjs', '--strict']);
    add('B. Claims', 'Public site: no invented rating or counts, every screenshot current', m.status === 0 ? 'PASS' : 'FAIL',
      (m.stdout + m.stderr).trim().split('\n').filter((l) => l.startsWith('- ')).join('\n') || (m.stdout + m.stderr).trim());
  } else add('B. Claims', 'Public site: no invented rating or counts, every screenshot current', 'FAIL', 'scripts/check-marketing-assets.mjs missing (branch chore/marketing-assets-refresh not merged)');

  add('B. Claims', '"Every SheOut trip is insured" is true', 'MANUAL',
    'a ride policy is active in the console (insurer, policy number, cover) and INSURANCE_REQUIRED_FOR_RIDES=true; otherwise the app must not show the insured card');
}

// ---------------------------------------------------------------- C. Production switches
{
  const yml = read('backend/src/main/resources/application.yml');
  const def = (key) => yml.match(new RegExp(`\\$\\{${key}:([^}]*)\\}`))?.[1];
  let prod = null;
  if (envFile) {
    prod = {};
    for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
      if (m) prod[m[1]] = m[2].replace(/^["']|["']$/g, '');
    }
  }
  // [key, launch value, why, test] - test(value) says whether the value is right for launch.
  const SWITCHES = [
    ['DEV_MODE_ENABLED', 'false', 'test sign-in codes and test numbers work while it is on', (v) => v === 'false'],
    ['LOG_OTP_CODES', 'false', 'sign-in codes would be written to the logs', (v) => v === 'false'],
    ['DEV_OTP_NUMBERS', '(blank)', 'fixed-code test numbers', (v) => v === ''],
    ['DEV_OTP_TEST_PREFIX', '(blank)', 'fixed-code test number range', (v) => v === ''],
    ['PROMOTIONAL_MESSAGES_ENABLED', 'false', 'no opt-in exists yet (O-10)', (v) => v === 'false'],
    ['ADMIN_COURT_SCREEN_ENABLED', 'false', 'only after the lawyer signs off the court-record check and its consent', (v) => v === 'false'],
    ['ADMIN_PHONE_LOGIN_ENABLED', 'false', 'staff sign in with passkeys only', (v) => v === 'false'],
    ['MARKETPLACE_SAMPLE_SHOPS', 'false', 'sample shops are labelled but not real; photo sources unknown (O-20)', (v) => v === 'false'],
    ['RETENTION_ENABLED', 'true', 'after O-8 periods are approved and a dry run is checked', (v) => v === 'true'],
    ['RETENTION_DRY_RUN', 'false', 'after the dry-run report is checked', (v) => v === 'false'],
    ['INSURANCE_REQUIRED_FOR_RIDES', 'true', 'rides pause if no policy is active', (v) => v === 'true'],
  ];
  for (const [key, want, why, ok] of SWITCHES) {
    const d = def(key);
    if (d === undefined) { add('C. Switches', key, 'FAIL', 'not found in application.yml'); continue; }
    if (prod && key in prod) add('C. Switches', `${key} = ${want}`, ok(prod[key]) ? 'PASS' : 'FAIL', `production ${ok(prod[key]) ? 'as wanted' : 'differs'}; ${why}`);
    else add('C. Switches', `${key} = ${want}`, 'MANUAL', `repo default "${d}"; check Render production (${why})`);
  }
  if (prod) {
    const k = prod.RAZORPAY_KEY_ID ?? '';
    add('C. Switches', 'Razorpay live keys', k.startsWith('rzp_live_') ? 'PASS' : 'FAIL', k ? (k.startsWith('rzp_live_') ? 'live key id' : 'not a live key id') : 'RAZORPAY_KEY_ID not in the file');
  } else add('C. Switches', 'Razorpay live keys, UPI methods on', 'MANUAL', 'RAZORPAY_KEY_ID starts rzp_live_; UPI enabled on the account (GET /v1/methods)');

  const entity = JSON.parse(read('frontend/design-system/src/legal/legal-entity.config.json'));
  const sms = entity.processors.find((p) => p.key === 'sms');
  add('C. Switches', 'SMS provider with DLT templates (sign-in codes, SOS texts)', sms?.live ? 'PASS' : 'FAIL',
    sms?.live ? 'marked live in legal-entity.config.json' : 'no SMS provider live: sign-in codes and SOS texts to contacts are not sent by SMS');
}

// ---------------------------------------------------------------- D. Security
{
  const pom = read('backend/pom.xml');
  const boot = pom.match(/<artifactId>spring-boot-starter-parent<\/artifactId>\s*<version>([^<]+)<\/version>/)?.[1] ?? '?';
  add('D. Security', 'Spring Boot in open-source support', /^3\.[0-4]\./.test(boot) ? 'FAIL' : 'PASS',
    `parent ${boot}${/^3\.[0-4]\./.test(boot) ? ' (out of support; O-22, branches chore/spring-boot-upgrade and chore/spring-boot-4-1)' : ''}`);

  const rep = 'backend/target/dependency-check-report.json';
  if (exists(rep)) {
    const r = JSON.parse(read(rep));
    const sev = { CRITICAL: 0, HIGH: 0 };
    for (const d of r.dependencies ?? []) for (const v of d.vulnerabilities ?? []) if (v.severity?.toUpperCase() in sev) sev[v.severity.toUpperCase()]++;
    const days = Math.round((Date.now() - statSync(join(root, rep)).mtimeMs) / 86_400_000);
    add('D. Security', 'Backend dependencies: no critical or high CVEs (OWASP)', sev.CRITICAL || sev.HIGH || days > 7 ? 'FAIL' : 'PASS',
      `${sev.CRITICAL} critical, ${sev.HIGH} high findings; report ${days} days old${days > 7 ? ' (re-run)' : ''}. Report from whatever branch was built last - re-run on the release branch`);
  } else add('D. Security', 'Backend dependencies: no critical or high CVEs (OWASP)', 'FAIL', 'no report: mvn -f backend/pom.xml org.owasp:dependency-check-maven:check -Dformats=JSON');

  if (!OFFLINE) {
    const a = run('npm', ['audit', '--omit=dev', '--json']);
    try {
      const v = JSON.parse(a.stdout).metadata.vulnerabilities;
      add('D. Security', 'Frontend dependencies: no critical or high advisories (npm audit)', v.critical || v.high ? 'FAIL' : 'PASS',
        `${v.critical} critical, ${v.high} high, ${v.moderate} moderate, ${v.low} low`);
    } catch { add('D. Security', 'Frontend dependencies (npm audit)', 'MANUAL', 'npm audit did not answer; run it by hand'); }
  }

  for (const app of ['customer-app', 'driver-app', 'marketing-site']) {
    const f = `frontend/${app}/vercel.json`;
    if (!exists(f)) { add('D. Security', `${app}: security headers`, 'FAIL', `${f} missing`); continue; }
    const keys = new Set((JSON.parse(read(f)).headers ?? []).flatMap((h) => h.headers.map((x) => x.key.toLowerCase())));
    const missing = ['x-content-type-options', 'referrer-policy'].filter((k) => !keys.has(k));
    if (!keys.has('x-frame-options') && !keys.has('content-security-policy')) missing.push('x-frame-options or frame-ancestors');
    add('D. Security', `${app}: security headers in vercel.json`, missing.length ? 'FAIL' : 'PASS', missing.length ? `missing ${missing.join(', ')}` : 'present');
    if (app !== 'marketing-site') {
      add('D. Security', `${app}: Content-Security-Policy enforced`, keys.has('content-security-policy') ? 'PASS' : 'FAIL',
        keys.has('content-security-policy-report-only') ? 'report-only (O-23: enforce after a preview deploy shows no violations)' : 'no CSP');
    }
  }
}

// ---------------------------------------------------------------- E. People and paperwork
{
  const check = 'docs/screen-reader-checklist.md';
  if (exists(check)) {
    const results = read(check).split('## Results')[1] ?? '';
    const runs = results.split('\n').filter((l) => /^\|\s*\d{4}-\d{2}-\d{2}/.test(l));
    add('E. People', 'Screen-reader pass by a person (NVDA, TalkBack, VoiceOver)', runs.length ? 'PASS' : 'FAIL', runs.length ? `${runs.length} run(s) recorded` : 'none recorded in the checklist');
  } else add('E. People', 'Screen-reader pass by a person', 'FAIL', `${check} missing (branch test/a11y-booking-flow not merged)`);
  for (const [item, detail] of [
    ['Mailboxes support@, grievance@, privacy@ receive mail', 'send a test to each from outside the company; someone reads grievance@ within 48 hours'],
    ['Processor DPAs accepted and filed (O-15)', 'Render, Vercel, Google Cloud/Firebase, AWS, Razorpay (and Anthropic, Sentry if switched on)'],
    ['Breach response owner named (docs/incident-response.md)', 'who tells the Data Protection Board within 72 hours, and the user message template'],
    ['Company bank account, GSTIN, payment gateway in the operating company', 'Razorpay activation needs the business details above'],
  ]) add('E. People', item, 'MANUAL', detail);
}

// ---------------------------------------------------------------- F. Live (--live): what visitors get today
if (LIVE) {
  const get = async (url) => {
    const r = await fetch(url, { redirect: 'follow', headers: { 'User-Agent': 'sheout-launch-gate (read-only)' }, signal: AbortSignal.timeout(20_000) });
    return { status: r.status, headers: r.headers, text: await r.text() };
  };
  for (const [name, url] of Object.entries(SITES)) {
    try {
      const page = await get(url);
      const csp = page.headers.get('content-security-policy') ? 'enforced' : page.headers.get('content-security-policy-report-only') ? 'report-only' : 'none';
      const hdr = ['x-content-type-options', 'referrer-policy', 'strict-transport-security'].filter((h) => !page.headers.get(h));
      add('F. Live', `${name}: headers served`, hdr.length ? 'FAIL' : 'PASS', `${url} HTTP ${page.status}; CSP ${csp}${hdr.length ? `; missing ${hdr.join(', ')}` : ''}`);
      const scripts = [...page.text.matchAll(/<script[^>]+src="(\/[^"]+\.js)"/g)].map((m) => m[1]);
      let all = page.text;
      for (const s of scripts) all += (await get(new URL(s, url).href)).text;
      const found = CLAIMS.filter(([re]) => re.test(all)).map(([, w]) => w);
      if (all.includes('TODO_FROM_OWNER')) found.push('TODO_FROM_OWNER placeholder');
      add('F. Live', `${name}: no removed claims or placeholders in what is served`, found.length ? 'FAIL' : 'PASS',
        found.length ? found.join(', ') : `${scripts.length} script(s) and the page scanned`);
    } catch (e) { add('F. Live', `${name}: reachable`, 'FAIL', `${url}: ${e.message}`); }
  }
  // The English the customer app shows first comes from content blocks.
  for (const prefix of ['about', 'faq', 'onboarding', 'safety']) {
    try {
      const r = await get(`${API}/api/v1/content?prefix=${prefix}`);
      if (r.status !== 200) { add('F. Live', `content blocks "${prefix}"`, 'MANUAL', `${API} answered HTTP ${r.status}`); continue; }
      const blocks = JSON.parse(r.text);
      const bad = Object.entries(blocks).flatMap(([k, v]) => CLAIMS.filter(([re]) => re.test(v)).map(([, w]) => `${k}: ${w}`));
      add('F. Live', `content blocks "${prefix}" free of removed claims`, bad.length ? 'FAIL' : 'PASS', bad.join('\n') || `${Object.keys(blocks).length} blocks`);
    } catch (e) { add('F. Live', `content blocks "${prefix}"`, 'MANUAL', `${API}: ${e.message}`); }
  }
  try {
    const mx = await resolveMx('sheoutride.com');
    add('F. Live', 'sheoutride.com receives mail (MX records)', mx.length ? 'PASS' : 'FAIL', mx.map((m) => m.exchange).join(', ') || 'no MX');
  } catch (e) { add('F. Live', 'sheoutride.com receives mail (MX records)', 'FAIL', e.code ?? e.message); }
}

// ---------------------------------------------------------------- report
const width = Math.min(64, Math.max(...results.map((r) => r.item.length)) + 2);
let area = '';
for (const r of results) {
  if (r.area !== area) console.log(`\n${(area = r.area)}`);
  const [first, ...rest] = r.detail.split('\n');
  console.log(`  ${r.status.padEnd(6)} ${r.item.padEnd(width)} ${first}`);
  for (const l of rest) console.log(`  ${''.padEnd(6)} ${''.padEnd(width)} ${l}`);
}
const n = (s) => results.filter((r) => r.status === s).length;
console.log(`\n${n('PASS')} pass, ${n('FAIL')} fail, ${n('MANUAL')} to confirm by hand${LIVE ? '' : ' (add --live to check what is served today)'}.`);
console.log(n('FAIL') ? 'NOT READY FOR LAUNCH.' : `No failures. Launch once the ${n('MANUAL')} manual items are confirmed.`);
process.exit(n('FAIL') ? 1 : 0);
