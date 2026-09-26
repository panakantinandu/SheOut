#!/usr/bin/env node
// SheOut load test: a busy launch evening in one city.
//
//   100 riders book within the first minute, then keep riding: quote, book,
//   watch the search, take the trip, pay from the wallet, wait, book again.
//   40 partners are online the whole time: location every 7 s, offers polled
//   every 4 s, trip list every 5 s, accepting what they are offered and
//   driving it end to end.
//
// Every interval is the real app's (see the constants below and their
// sources). Nothing is mocked on the server side: dispatch, the pickup code,
// both geofences, OSRM and the wallet all run for real.
//
// NEVER POINT THIS AT PRODUCTION. It needs DEV_OTP_NUMBERS for its 140
// accounts and marks them verified directly in the database.
//
// Usage:
//   node sheout-load.mjs --api http://localhost:8080 --minutes 15 \
//        --riders 100 --partners 40 --out D:/sheout-work/load
//   (see README.md for the environment the backend must be started with)
import fs from 'fs';
import { execFileSync } from 'child_process';

const arg = (name, def) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : def;
};
const API = arg('api', 'http://localhost:8080');
const MINUTES = Number(arg('minutes', '15'));
const RIDERS = Number(arg('riders', '100'));
const PARTNERS = Number(arg('partners', '40'));
const OUT = arg('out', './load-results');
const PSQL = arg('psql', 'C:/Program Files/PostgreSQL/18/bin/psql.exe');
const REDIS_CLI = arg('redis-cli', 'C:/Program Files/Memurai/memurai-cli.exe');
const REDIS_PORT = arg('redis-port', '6380');
const CODE = '246810';
if (/onrender\.com|sheoutride\.com/.test(API)) throw new Error('Refusing to load-test production.');
fs.mkdirSync(OUT, { recursive: true });

// The apps' real intervals.
const PARTNER_LOCATION_MS = 7000;   // driver-app LocationBroadcastContext LOCATION_SEND_MS
const PARTNER_OFFER_POLL_MS = 4000; // driver-app Home OFFER_POLL_MS
const PARTNER_TRIPS_POLL_MS = 5000; // driver-app Home BOOKINGS_POLL_MS
const RIDER_BOOKING_POLL_MS = 3000; // customer-app Tracking POLL_INTERVAL_MS
const RIDER_DRIVER_POLL_MS = 7000;  // customer-app Tracking DRIVER_LOCATION_POLL_MS
const RIDER_NEARBY_POLL_MS = 10000; // customer-app useNearbyDrivers POLL_MS

const CENTRE = { lat: 17.3850, lng: 78.4867 };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const rand = (a, b) => a + Math.random() * (b - a);
const jitter = (ms) => ms * rand(0.9, 1.1);
function pointNear(centre, minKm, maxKm) {
  const km = rand(minKm, maxKm), bearing = rand(0, 2 * Math.PI);
  return {
    lat: centre.lat + (km / 111) * Math.cos(bearing),
    lng: centre.lng + (km / (111 * Math.cos((centre.lat * Math.PI) / 180))) * Math.sin(bearing),
  };
}
function stepToward(from, to, metres) {
  const dLat = (to.lat - from.lat) * 111000, dLng = (to.lng - from.lng) * 111000 * Math.cos((from.lat * Math.PI) / 180);
  const dist = Math.hypot(dLat, dLng);
  if (dist <= metres) return { ...to };
  const f = metres / dist;
  return { lat: from.lat + (to.lat - from.lat) * f, lng: from.lng + (to.lng - from.lng) * f };
}

// ---------------------------------------------------------------- metrics
const stats = new Map(); // route -> { lat: [], codes: {} }
const timeline = [];     // { t, route, ms, status }
let running = true;
const started = { at: 0 };
async function call(route, method, path, token, body) {
  const t0 = performance.now();
  let status = 0, json = null;
  try {
    const res = await fetch(API + path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(30000),
    });
    status = res.status;
    const text = await res.text();
    try { json = text ? JSON.parse(text) : null; } catch { json = null; }
  } catch (e) {
    status = e.name === 'TimeoutError' ? -2 : -1; // -1 network error, -2 client timeout (30 s)
  }
  const ms = performance.now() - t0;
  if (started.at) {
    let s = stats.get(route);
    if (!s) stats.set(route, (s = { lat: [], codes: {} }));
    s.lat.push(ms);
    s.codes[status] = (s.codes[status] ?? 0) + 1;
    timeline.push({ t: Date.now() - started.at, route, ms: Math.round(ms), status });
  }
  return { status, body: json };
}
const outcome = { booked: 0, matched: 0, noDrivers: 0, started: 0, completedByPartner: 0, paid: 0, bookRefused: {}, matchSeconds: [] };

// ---------------------------------------------------------------- setup
const sql = (q) => execFileSync(PSQL, ['-h', 'localhost', '-U', 'sheout', '-d', 'sheout', '-tAc', q],
  { env: { ...process.env, PGPASSWORD: 'sheout' } }).toString().trim();
const redis = (...a) => execFileSync(REDIS_CLI, ['-p', REDIS_PORT, ...a]).toString().trim();
const riderPhone = (i) => `+9197000${String(i).padStart(5, '0')}`;
const partnerPhone = (i) => `+9196000${String(i).padStart(5, '0')}`;

async function signIn(phone, role) {
  await call('setup', 'POST', '/api/v1/auth/otp/request', null, { phoneNumber: phone, role });
  const r = await call('setup', 'POST', '/api/v1/auth/otp/verify', null, { phoneNumber: phone, code: CODE, role });
  if (!r.body?.accessToken) throw new Error(`sign-in ${phone} ${role}: ${r.status} ${JSON.stringify(r.body)}`);
  return { token: r.body.accessToken, id: r.body.accountId };
}

async function setup() {
  console.log(`setup: signing in ${RIDERS} riders and ${PARTNERS} partners`);
  const riders = [], partners = [];
  for (let i = 0; i < RIDERS; i++) riders.push({ i, phone: riderPhone(i), ...(await signIn(riderPhone(i), 'CUSTOMER')) });
  for (let i = 0; i < PARTNERS; i++) partners.push({ i, phone: partnerPhone(i), ...(await signIn(partnerPhone(i), 'DRIVER')) });
  const rIds = riders.map((r) => `'${r.id}'`).join(','), pIds = partners.map((p) => `'${p.id}'`).join(',');
  // Staging seed: verified, profiled, funded, and no leftovers from a previous run.
  sql(`update bookings set status='CANCELLED', cancelled_at=now() where status in ('REQUESTED','MATCHED','ACCEPTED','IN_PROGRESS') and (customer_id in (${rIds}) or driver_id in (${pIds}))`);
  sql(`update bookings set payment_settled_at=now() where status='COMPLETED' and payment_settled_at is null and (customer_id in (${rIds}) or driver_id in (${pIds}))`);
  sql(`update verification_records set gender_verification_status='VERIFIED' where account_id in (${rIds})`);
  sql(`update verification_records set gender_verification_status='VERIFIED', police_verification_status='VERIFIED' where account_id in (${pIds})`);
  sql(`update customer_profiles set name='Load Rider' where account_id in (${rIds})`);
  sql(`update driver_profiles set name='Load Partner', date_of_birth='1990-01-01', vehicle_type='BIKE', vehicle_registration_number='TS09LT' || lpad((random()*9999)::int::text,4,'0'), profile_photo_key='loadtest/photo', online_status='OFFLINE' where account_id in (${pIds})`);
  sql(`insert into rider_wallets (id, customer_account_id, balance, version, created_at, updated_at) select gen_random_uuid(), id, 100000, 0, now(), now() from accounts where id in (${rIds}) on conflict (customer_account_id) do update set balance = 100000`);
  // The per-rider hourly booking cap is a real rule, but a previous run's
  // bookings in the last hour would otherwise count against this one.
  for (const k of redis('--scan', '--pattern', 'rl:booking-create:*').split(/\r?\n/).filter(Boolean)) redis('DEL', k);
  return { riders, partners };
}

// ---------------------------------------------------------------- partner
const pickupCodes = new Map(); // bookingId -> code (the rider reading it aloud)

async function partnerLoop(p) {
  p.pos = pointNear(CENTRE, 0.5, 7);
  let r = await call('partner go online', 'POST', '/api/v1/users/driver/me/status', p.token, { status: 'ONLINE', lat: p.pos.lat, lng: p.pos.lng });
  if (r.status !== 200) console.log(`partner ${p.i} could not go online: ${r.status} ${JSON.stringify(r.body)}`);
  p.trip = null;

  const loops = [
    (async () => { // location, every 7 s, moving at ~25 km/h toward whatever she is heading for
      while (running) {
        const target = p.trip?.target;
        p.pos = target ? stepToward(p.pos, target, (25000 / 3600) * (PARTNER_LOCATION_MS / 1000)) : stepToward(p.pos, pointNear(p.pos, 0, 0.05), 30);
        await call('partner location', 'POST', '/api/v1/dispatch/location', p.token, { lat: p.pos.lat, lng: p.pos.lng });
        await sleep(jitter(PARTNER_LOCATION_MS));
      }
    })(),
    (async () => { // offers, every 4 s
      while (running) {
        if (!p.trip) {
          const o = await call('partner offer poll', 'GET', '/api/v1/dispatch/offers/me', p.token);
          if (o.status === 200 && o.body?.bookingId) {
            await sleep(rand(1000, 5000)); // reading it
            const a = await call('partner claim offer', 'POST', `/api/v1/dispatch/offers/${o.body.bookingId}/accept`, p.token);
            if (a.status === 200) {
              const b = await call('partner accept trip', 'POST', `/api/v1/bookings/${o.body.bookingId}/accept`, p.token);
              if (b.status === 200) {
                p.trip = { id: o.body.bookingId, pickup: b.body.pickup, drop: b.body.drop, target: b.body.pickup, phase: 'PICKUP' };
                void driveTrip(p);
              }
            }
          }
        }
        await sleep(jitter(PARTNER_OFFER_POLL_MS));
      }
    })(),
    (async () => { // trip list + payment hold, every 5 s
      while (running) {
        await Promise.all([
          call('partner trips poll', 'GET', '/api/v1/bookings/me', p.token),
          call('partner payment-hold poll', 'GET', '/api/v1/bookings/me/payment-hold', p.token),
        ]);
        await sleep(jitter(PARTNER_TRIPS_POLL_MS));
      }
    })(),
  ];
  await Promise.all(loops);
}

async function driveTrip(p) {
  const trip = p.trip;
  const near = (a, b) => Math.hypot((a.lat - b.lat) * 111000, (a.lng - b.lng) * 111000 * 0.95) < 40;
  const deadline = Date.now() + 20 * 60000;
  // To the pickup; the rider reads out her code; start.
  while (running && !near(p.pos, trip.pickup) && Date.now() < deadline) await sleep(1000);
  while (running && !pickupCodes.has(trip.id) && Date.now() < deadline) await sleep(1000);
  const s = await call('partner start trip', 'POST', `/api/v1/bookings/${trip.id}/start`, p.token, { pickupCode: pickupCodes.get(trip.id) ?? '0000' });
  if (s.status !== 200) { p.trip = null; return; }
  outcome.started++;
  trip.target = trip.drop;
  while (running && !near(p.pos, trip.drop) && Date.now() < deadline) await sleep(1000);
  // Her last location report must be at the drop: wait for the next send.
  await sleep(PARTNER_LOCATION_MS + 500);
  const c = await call('partner complete trip', 'POST', `/api/v1/bookings/${trip.id}/complete`, p.token);
  if (c.status === 200) outcome.completedByPartner++;
  p.trip = null;
}

// ---------------------------------------------------------------- rider
async function riderLoop(r, startDelayMs) {
  await sleep(startDelayMs);
  while (running) {
    const pickup = { label: 'Load pickup', ...pointNear(CENTRE, 0, 5) };
    const drop = { label: 'Load drop', ...pointNear(pickup, 1.5, 4) };
    // The booking screen: partners nearby, then a price.
    await call('rider nearby partners', 'GET', `/api/v1/dispatch/nearby-drivers?lat=${pickup.lat}&lng=${pickup.lng}&category=BIKE`, r.token);
    await call('rider fare quote', 'POST', '/api/v1/bookings/quote', r.token, { type: 'RIDE', category: 'BIKE', pickup, drop });
    await sleep(rand(3000, 8000));
    const created = await call('rider create booking', 'POST', '/api/v1/bookings', r.token, { type: 'RIDE', category: 'BIKE', pickup, drop });
    if (created.status !== 201) {
      const why = created.body?.error ?? created.status;
      outcome.bookRefused[why] = (outcome.bookRefused[why] ?? 0) + 1;
      await sleep(30000);
      continue;
    }
    outcome.booked++;
    const id = created.body.id, bookedAt = Date.now();
    let status = 'REQUESTED', lastDriverPoll = 0, codeFetched = false, paid = false;
    while (running) {
      await sleep(jitter(RIDER_BOOKING_POLL_MS));
      const b = await call('rider booking poll', 'GET', `/api/v1/bookings/${id}`, r.token);
      if (b.status !== 200) continue;
      if (status === 'REQUESTED' && b.body.status !== 'REQUESTED' && b.body.status !== 'NO_DRIVERS_AVAILABLE') {
        outcome.matched++;
        outcome.matchSeconds.push((Date.now() - bookedAt) / 1000);
      }
      status = b.body.status;
      if (status === 'ACCEPTED' || status === 'IN_PROGRESS') {
        let freshPosition = false;
        if (Date.now() - lastDriverPoll >= RIDER_DRIVER_POLL_MS) {
          lastDriverPoll = Date.now();
          const l = await call('rider partner-location poll', 'GET', `/api/v1/dispatch/bookings/${id}/driver-location`, r.token);
          freshPosition = l.status === 200;
        }
        // As the app does: the code is asked for again each time her partner's position updates.
        if (status === 'ACCEPTED' && !codeFetched && freshPosition) {
          const c = await call('rider pickup code', 'GET', `/api/v1/bookings/${id}/pickup-code`, r.token);
          if (c.status === 200) { pickupCodes.set(id, c.body.pickupCode); codeFetched = true; }
        }
      }
      if (status === 'COMPLETED' && !paid) {
        const pay = await call('rider pay from wallet', 'POST', `/api/v1/payments/bookings/${id}/wallet`, r.token);
        if (pay.status === 200) { outcome.paid++; paid = true; }
        break;
      }
      if (status === 'NO_DRIVERS_AVAILABLE') { outcome.noDrivers++; break; }
      if (status === 'CANCELLED') break;
    }
    await sleep(rand(60000, 180000)); // before her next trip
  }
}

// ---------------------------------------------------------------- server-side sampling
const samples = [];
function redisInfo() {
  const info = Object.fromEntries(redis('INFO').split(/\r?\n/).filter((l) => l.includes(':')).map((l) => l.split(/:(.*)/s).slice(0, 2)));
  return {
    commands: Number(info.total_commands_processed), opsPerSec: Number(info.instantaneous_ops_per_sec),
    usedMemoryMb: Number(info.used_memory) / 1048576, clients: Number(info.connected_clients),
  };
}
function processStats() {
  try {
    const out = execFileSync('powershell', ['-NoProfile', '-Command',
      "Get-Process java,osrm-routed -ErrorAction SilentlyContinue | ForEach-Object { '{0}|{1}|{2}|{3}' -f $_.ProcessName,$_.Id,[int]($_.WorkingSet64/1MB),[math]::Round($_.TotalProcessorTime.TotalSeconds,1) }"]).toString();
    return out.trim().split(/\r?\n/).filter(Boolean).map((l) => { const [name, pid, mb, cpu] = l.split('|'); return { name, pid: Number(pid), mb: Number(mb), cpuSeconds: Number(cpu) }; });
  } catch { return []; }
}

// ---------------------------------------------------------------- report
function pct(sorted, p) { return sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))] : 0; }
function report(durationS, redisStart, redisEnd) {
  const EXPECTED = { 'rider partner-location poll': [404], 'partner claim offer': [404, 409], 'rider pickup code': [404] };
  const rows = [];
  let all = 0, bad = 0;
  for (const [route, s] of [...stats.entries()].sort()) {
    if (route === 'setup') continue;
    const lat = [...s.lat].sort((a, b) => a - b);
    const n = lat.length;
    const errors = Object.entries(s.codes).filter(([c]) => { const code = Number(c); return code <= 0 || code >= 500 || code === 401 || code === 429 || (code >= 400 && !(EXPECTED[route] ?? []).includes(code) && code !== 409 && code !== 404); })
      .reduce((a, [, v]) => a + v, 0);
    all += n; bad += errors;
    rows.push({ route, n, rps: n / durationS, p50: pct(lat, 50), p95: pct(lat, 95), p99: pct(lat, 99), max: lat[n - 1], errors, codes: s.codes });
  }
  const f = (x) => x.toFixed(0).padStart(6);
  let text = `SheOut load test - ${new Date().toISOString()}\nAPI ${API}, ${RIDERS} riders, ${PARTNERS} partners, ${(durationS / 60).toFixed(1)} min\n\n`;
  text += 'endpoint'.padEnd(30) + '     n  req/s    p50    p95    p99    max  errors  status codes\n';
  for (const r of rows) text += `${r.route.padEnd(30)}${String(r.n).padStart(6)} ${r.rps.toFixed(2).padStart(6)}${f(r.p50)}${f(r.p95)}${f(r.p99)}${f(r.max)}  ${String(r.errors).padStart(6)}  ${JSON.stringify(r.codes)}\n`;
  const allLat = [...stats.entries()].filter(([k]) => k !== 'setup').flatMap(([, s]) => s.lat).sort((a, b) => a - b);
  text += `\nALL: ${all} requests, ${(all / durationS).toFixed(1)} req/s, p50 ${pct(allLat, 50).toFixed(0)} ms, p95 ${pct(allLat, 95).toFixed(0)} ms, p99 ${pct(allLat, 99).toFixed(0)} ms, errors ${bad} (${((100 * bad) / Math.max(1, all)).toFixed(2)}%)\n`;
  const redisCmds = redisEnd.commands - redisStart.commands;
  text += `\nRedis: ${redisCmds} commands in ${(durationS / 60).toFixed(1)} min = ${(redisCmds / durationS).toFixed(1)}/s; `
    + `${Math.round((redisCmds / durationS) * 86400).toLocaleString('en-US')}/day at this rate; peak memory ${Math.max(...samples.map((s) => s.redis.usedMemoryMb)).toFixed(1)} MB; peak clients ${Math.max(...samples.map((s) => s.redis.clients))}\n`;
  const mt = [...outcome.matchSeconds].sort((a, b) => a - b);
  text += `\nTrips: ${outcome.booked} booked, ${outcome.matched} matched (median ${pct(mt, 50).toFixed(0)} s to match), ${outcome.noDrivers} no partner available, `
    + `${outcome.started} started with a pickup code, ${outcome.completedByPartner} completed at the drop, ${outcome.paid} paid; booking refused: ${JSON.stringify(outcome.bookRefused)}\n`;
  const procs = {};
  for (const s of samples) for (const p of s.procs) { procs[p.name] ??= { maxMb: 0, first: p, last: p }; procs[p.name].maxMb = Math.max(procs[p.name].maxMb, p.mb); procs[p.name].last = p; }
  for (const [name, p] of Object.entries(procs)) {
    text += `${name}: peak working set ${p.maxMb} MB, CPU ${(p.last.cpuSeconds - p.first.cpuSeconds).toFixed(0)} s over the run = ${((100 * (p.last.cpuSeconds - p.first.cpuSeconds)) / durationS).toFixed(0)}% of one core on average\n`;
  }
  // Per-minute latency and errors, to see whether anything degrades over time.
  text += '\nminute   requests   p95 ms   errors\n';
  for (let m = 0; m * 60000 < durationS * 1000; m++) {
    const slice = timeline.filter((x) => x.t >= m * 60000 && x.t < (m + 1) * 60000 && x.route !== 'setup');
    const l = slice.map((x) => x.ms).sort((a, b) => a - b);
    const e = slice.filter((x) => x.status <= 0 || x.status >= 500 || x.status === 429 || x.status === 401).length;
    text += `${String(m + 1).padStart(6)} ${String(slice.length).padStart(10)} ${String(pct(l, 95)).padStart(8)} ${String(e).padStart(8)}\n`;
  }
  return { text, rows };
}

// ---------------------------------------------------------------- run
const { riders, partners } = await setup();
console.log(`running ${MINUTES} min: ${riders.length} riders booking within the first minute, ${partners.length} partners online`);
const redisStart = redisInfo();
started.at = Date.now();
const sampler = (async () => {
  while (running) {
    samples.push({ t: Date.now() - started.at, redis: redisInfo(), procs: processStats() });
    await sleep(15000);
  }
})();
const work = [
  ...partners.map((p) => partnerLoop(p)),
  ...riders.map((r) => riderLoop(r, rand(0, 60000))),
];
await sleep(MINUTES * 60000);
running = false;
const durationS = (Date.now() - started.at) / 1000;
const redisEnd = redisInfo();
samples.push({ t: Date.now() - started.at, redis: redisEnd, procs: processStats() });
const { text, rows } = report(durationS, redisStart, redisEnd);
fs.writeFileSync(`${OUT}/report.txt`, text);
fs.writeFileSync(`${OUT}/results.json`, JSON.stringify({ rows, outcome, samples }, null, 1));
console.log(text);
// Partners go offline so the next run starts clean; the loops finish their current step.
await Promise.race([Promise.allSettled([...work, sampler]), sleep(40000)]);
for (const p of partners) await fetch(`${API}/api/v1/users/driver/me/status`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${p.token}` }, body: JSON.stringify({ status: 'OFFLINE' }) }).catch(() => undefined);
process.exit(0);
