# Production readiness: scaling, failure and recovery

Measured 2026-10-02 against commit `2679483` plus the changes listed in
section P. Every number here comes from a test that was run, and each one
says where it ran. Anything not measured says **NOT YET MEASURED**, followed
by the test that would measure it.

**Where the numbers come from.** Unless a row says otherwise, the load
numbers come from one Windows laptop with 8 logical CPUs and 8 GB of RAM.
The backend JVM ran with production's flags and was pinned to **one CPU
core**, which matches production's Render Standard instance (1 CPU, 2 GB).
Postgres 18, Redis (Memurai 8), the local OSRM router and the load generator
all shared the same machine. Absolute numbers on Render will differ.
Relative findings, such as which resource runs out first and what a change
does, carry over. The 2026-09-27 staging runs in `loadtest/README.md` are the
only measurements taken on Render itself.

---

## A. Current architecture (from the repository)

| Part | What it is | Where |
|---|---|---|
| Backend | Spring Boot 3.3.4 modular monolith, Java 21, one Docker image | Render web service `sheout-backend`, **Standard (1 CPU, 2 GB)**, Singapore, **1 instance** (`render.yaml` sets no `numInstances`) |
| JVM | heap 60% of the container, SerialGC, C1 compiler only, class-data archive | `backend/Dockerfile` |
| Load balancer / TLS | Render's own proxy, with Cloudflare in front (`CLIENT_IP_HEADER=CF-Connecting-IP`) | Render and Cloudflare (no repo config) |
| Health check | `/actuator/health/readiness`. It returns 503 until the warm-up finishes. | `render.yaml` |
| Database | Postgres, Render `sheout-db`, **Pro 4 GB, 15 GB disk** (set in the dashboard on purpose, so it's not in `render.yaml`) | Hikari pool: Spring default, **10 connections** per instance |
| Migrations | Flyway (V1-V54), run by each instance at startup | |
| Redis | Render Key Value `sheout-redis`, **Starter 256 MB** | Holds OTP codes, rate limits, the partner geo index, offers, dispatch rounds, trip trails, job locks |
| Router | Self-hosted OSRM `sheout-osrm` (private service, Starter 512 MB), 110 km map | `infra/osrm` |
| Realtime | **None: no WebSocket or SSE.** The apps poll: rider trip 3 s, partner position 7 s, offers 4 s, partner Home 5 s, nearby partners 10 s. | `frontend/*/src` |
| Background work | 5 `@Scheduled` jobs (search sweeper, stale-search reaper, trip watch, shift-check expiry, re-engagement), each behind a Redis lock (`ClusterLock`). Notifications go through an in-process `@Async` pool (2-8 threads, queue 500). | |
| Events | In-process Spring events between modules, with no broker (the modular-monolith decision) | |
| Files | Uploaded photos and documents are stored **in Postgres** (`DatabaseDocumentStorage`), so there are no files on the instance disk | |
| Push / SMS / email | FCM web push, Twilio (not configured), SMTP | |
| Payments | Razorpay orders, QR codes and webhooks | |
| Face check | AWS Rekognition (ap-south-1), capped at 4,500 calls a month | |
| Apps | Two Vite PWAs on Vercel, deployed with the CLI | |
| CI | GitHub Actions: backend tests on Postgres 16 and Redis 7, and both app builds. It deploys nothing. | `.github/workflows/ci.yml` |
| Monitoring | Sentry (when `SENTRY_DSN` is set), Render Metrics, request id in logs. Actuator exposes only health and info. | |
| Staging | `sheout-backend-staging`, with its own database, Redis and OSRM (free tier between tests) | `render.yaml` |

## B. Current instance capacity

### One instance, one core: load steps

Each step starts with every rider booking within the first minute (a sudden
spike), then settles into the apps' real polling. Partners drive the trips
for real: offer, pickup code, trip, completion, payment.

| Riders / partners | Build | Throughput | p95 overall | First-minute p95 | Errors | CPU avg / peak |
|---|---|---|---|---|---|---|
| 100 / 40 | before | 52 req/s | 91 ms | 182 ms | **0** | 0.14 / 0.40 |
| 300 / 120 | before | 145 req/s | 1,517 ms | 4,661 ms | 116 (0.33%) | 0.53 / 1.00 |
| 300 / 120 | **after** | 149 req/s | **660 ms** | 1,776 ms | **0** | 0.47 / 1.06 |
| 600 / 240 | before | 146 req/s | 21,992 ms | 14,723 ms | 904 (2.58%) | 0.68 / 1.09 |
| 600 / 240 | **after** | 202 req/s | 10,179 ms | 12,772 ms | 194 (0.40%) | 0.79 / 1.03 |
| 1,000 / 400 | after | 112 req/s | 30,029 ms | 21,323 ms | 14,219 (53%) | 0.44 / 1.08 |

"Before" is the code as it was. "After" adds the preview cache and the
promotion-lock fix (section P).

**Safe envelope for one Standard instance, as measured:** up to **300
riders booking within the same minute** (about 150 req/s sustained), with 0
errors and the spike p95 under 2 s. At 600 riders booking in one minute the
first two minutes degrade badly (p95 10-20 s, 0.4% errors) and it then
recovers. At 1,000 in one minute it fails.

On Render (staging, 2026-09-27), 100 riders and 40 partners ran 16 minutes
with 0 errors, p95 389 ms measured from Kansas City (about 150 ms at the
server), and memory peaking at about 500 MB of 2 GB.

**Memory is not a constraint.** The working set peaked at 757 MB in the
worst run, against a 2 GB instance.

**Redis is not a constraint.** It peaked at about 1,700 ops/s and used a
few MB of memory.

### Two instances behind a round-robin balancer (TEST 6/7)

| Riders | Build | Throughput | Errors | Note |
|---|---|---|---|---|
| 600 | before promotion fix | 195 req/s | 1.03% | **No better than one instance.** The traffic split was exactly even (26,152 / 26,151 requests), so the shared database lock was the limit. |
| 1,000 | after | 112 req/s | 24% (vs 53% on one) | Better, still overloaded. The laptop itself was saturated, running 2 JVMs, Postgres, the router and the load generator. |

**NOT YET MEASURED:** the ceiling of two instances on Render. The test that
would measure it:
1. Put staging on Standard with 2 instances.
2. Run `node loadtest/sheout-load.mjs --api https://sheout-backend-staging.onrender.com --code 123456 --rider-prefix +919999995 --partner-prefix +919999996 --db-url "$LOAD_DB_URL" --no-redis --minutes 4 --riders 600 --partners 240`.
3. Repeat with `--riders 1000 --partners 400`.
4. Read CPU and memory from Render Metrics.

## C. Single points of failure

| Component | Count | If it fails |
|---|---|---|
| Backend instance | 1 | Total outage until Render restarts it. **Trips survive in Postgres** (TEST 1). |
| Postgres | 1 (no replica) | Total outage. Nothing reports false success, and trips resume when it returns (TEST 9). |
| Redis | 1 | Sign-in, dispatch, location and rate limits fail. Trip state survives. It recovers by itself within about 30 s of Redis returning, and searches it cut off are ended by the database sweep (measured). |
| OSRM | 1 | Fares fall back to straight-line estimates, which is safe. |
| Render region | Singapore only | Regional outage means a full outage. There's no second region and no DR environment. |
| Notification queue | in-process | Queued pushes are lost if the instance **crashes** (graceful shutdown now drains them, section P). |

## D. Vertical scaling

- **Supported by the app as it is.** The JVM sizes itself as a share of the
  container (`MaxRAMPercentage=60`), so a bigger plan gets a bigger heap
  with no change.
- **SerialGC and C1-only** were chosen for 1 CPU. On a 2+ CPU plan, drop
  `-XX:+UseSerialGC -XX:TieredStopAtLevel=1` in `backend/Dockerfile` to get
  G1 and the full compiler. NOT YET MEASURED on 2 CPU.
- **CPU was the limit once the lock was fixed** (600 riders: 0.79 average,
  1.03 peak of one core), so more CPU raises the burst a single instance can
  absorb.
- **The database pool and the database are separate limits** (see G). A
  larger instance still has 10 connections unless
  `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` is raised.
- **Downtime.** On Render, changing the instance type is a redeploy. With a
  health check set (it is), Render starts the new instance and moves
  traffic only once it is ready, so a plan change behaves like a deploy (see
  section 16). NOT YET MEASURED on Render: watch Metrics and the uptime
  monitor during the first plan change.

## E. Horizontal scaling: is the backend stateless?

Checked line by line for anything that breaks when request 1 goes to server
A and request 2 to server B:

| State | Where it lives | Safe across instances? |
|---|---|---|
| Sign-in | Stateless JWT; sessions and revocation in Postgres `account_sessions`; `SessionCreationPolicy.STATELESS` | Yes. No sticky sessions needed. |
| OTP codes, rate limits | Redis | Yes. The rate limit held across both instances in the race test. |
| Trip state | Postgres, row-locked transitions | Yes (TEST 1, 8) |
| Partner positions, offers, rounds, trails | Redis | Yes (TEST 8: rider on A saw the position partner reported to B) |
| Scheduled jobs | Every job behind `ClusterLock` (Redis SET NX) | Yes, one runner at a time |
| Uploaded files | Postgres | Yes. Nothing is on the local disk. |
| Realtime | Polling, no connections | Yes. Nothing to share. |
| Notification queue | In-process memory | Works (each instance sends its own), but a crash loses its queue |
| Preview eligibility cache (new) | In-process, 15 s | Yes. It only affects map markers, never decisions. |

**Verdict: yes.** A second instance needs no code change, only a setting,
and this was proven with two instances (TEST 7, 8). Two settings have to
move with it:
1. **Database connections.** Each instance opens up to 10, so N instances
   open up to 10N. Check `SHOW max_connections;` on `sheout-db` before
   adding instances. NOT YET MEASURED for this plan, so don't assume a
   number.
2. **Migrations.** Each instance runs Flyway at boot. Flyway's lock makes
   that safe, but see section 17.

## F. Autoscaling

- **Current state:** 1 instance, set by hand. No autoscaling is configured.
- **What it needs:** nothing in the code (E). On Render, manual scaling is
  the instance count on the service's Scaling page. Autoscaling (min/max
  plus a CPU or memory target) depends on the workspace plan. **Check your
  workspace plan in the dashboard**; the repository can't see it.
- **How fast a second instance arrives:** startup measured at 11.3 s plus
  1.5 s of warm-up on production (2026-09-27), plus Render's provisioning
  time (NOT YET MEASURED). So about tens of seconds once the instance is
  running.
- **Not recommended yet:** autoscaling on CPU. A booking spike saturates CPU
  within the first minute (B), which is faster than an autoscaler reacts.
  For planned busy evenings, scale up by hand beforehand.

## G. Database scalability

- **Hot row, found and fixed.** Every booking reserved its signup credit by
  locking the single `promotions` row, and held that lock through the first
  dispatch round. In a 1,000-rider spike, **334 of 545 sampled busy
  connections were waiting on that one row**, for up to 29.6 s. After the
  fix, 0 lock waits were sampled.
- **Pool size.** Once the lock was fixed, the next limit at 1,000 riders
  was the 10-connection pool. Thread dumps showed 168-193 of 200 request
  threads waiting for a connection, with no nested-transaction deadlock.
  Every request uses at least one connection (`SessionService.checkLive`),
  and dispatch eligibility adds about 7 queries per candidate partner.
- **Indexes.** Bookings are indexed by customer, driver, status, and status
  plus search start. Sessions, notifications, payments, chat and trip
  alerts are indexed for the queries they serve. No slow-query log was
  captured. NOT YET MEASURED: enable `pg_stat_statements` on `sheout-db`
  and read the top 20 by total time after a busy evening.
- **High-write tables.** Locations and trails are **not** written to
  Postgres. They live only in Redis, with expiry. `booking_events`,
  `notification_log` and `chat_messages` are append-only and indexed.
- **Read replicas and sharding:** no demonstrated need.
- **Growth risk, found and fixed:** see H.

## H. Location scalability

- **Per partner:** one report every 7 s, so **0.14 per second**.
  - 100 partners: 14/s
  - 1,000: 143/s
  - 10,000: 1,429/s
  - 100,000: 14,286/s
- **Path:** phone → API → Redis (GEOADD plus a timestamp, a trail point
  while on a trip, an approach check) → read by the rider every 7 s. **No
  Postgres write per report**, but the request itself needs a database
  connection for the session check.
- **Measured:** partner location p95 was 857 ms at 300 riders on one core.
  It's not the bottleneck: it slows only when the whole instance is
  saturated.
- **Partner Home poll (fixed).** `GET /bookings/me` returned her entire
  history every 5 s:
  - 500 trips: 342 KB, 44 ms
  - 2,000 trips: **1.37 MB, 153 ms** (about 1 GB of her mobile data an hour)

  With `?recent=50`: **34 KB and 20 ms whatever the history.**

## I. Matching

- **One partner, one trip.** A partner has one offer slot (Redis SET NX),
  each booking has a claim key, and the availability check refuses a
  partner on a live trip. A second partner trying to take a claimed offer
  is refused (e2e). Two `complete` calls at once: one succeeds, one gets 409.
- **One rider, one ride (bug found and fixed).** **Eight simultaneous
  booking requests from one rider created eight live bookings**, each sent
  to a different partner. A per-rider Postgres advisory lock now serialises
  them. After the fix, 8 requests spread across two instances made 1
  booking and 7 refusals, in every trial.
- **Cost.** Each candidate check is about 7 database reads. The rider-facing
  previews (nearby partners, quote ETA) now reuse it for 15 s. The offer
  path stays fully fresh.

## J. Realtime

Nothing to scale: there are no WebSockets. The rider on A and the partner on
B both read the same Redis and Postgres (TEST 8 passed). The cost of
polling is counted in B.

## K. Active-trip survival (TEST 1, 8)

The rider used instance A and the partner used instance B. The trip was
accepted and started, then **B was killed with no shutdown**. The partner
carried on through A. Results:
- same trip, still `IN_PROGRESS`, same partner
- no second booking
- the rider followed the partner's position
- 2 simultaneous completes produced 1 completion and **1 payment row**
- 2 simultaneous wallet payments produced **1 debit and 1 partner credit**

**17/17 passed.**

## L. App-crash recovery (TEST 2, 3, 4)

Each app was reopened cold, with only the sign-in kept, as a phone keeps it.
**9/9 passed:**
- The rider app reopened straight onto her trip (new, see P).
- Going back to Home didn't bounce her into the trip again, and Home showed
  the "You are on your trip" card.
- **Network off for 8 s, then back on:** the trip screen stayed on her trip
  and caught up.
- The partner app reopened with her active trip on Home, one tap from the
  trip. Its location sharing restarts from the server's state.

## M. Backend failure recovery

| Failure | What breaks | What survives | Recovery | Data loss / inconsistency |
|---|---|---|---|---|
| Instance crash (TEST 1) | Requests in flight on it | All trip, payment and booking state | Other instance or restart; retries are safe | None seen |
| Database down (TEST 9) | Every request (500) | Trip state; nothing half-written | The same instance recovers on its own when the database returns | **No false success**: the failed complete wrote no payment, and the retry made one |
| Redis down | Sign-in, dispatch, location, offers | Trip state; reads from the database | Automatic within the client's reconnect backoff (about 30 s) | Live searches are lost and **ended by the database sweep** (measured: `NO_DRIVERS_AVAILABLE`). Offers in flight are lost. |
| Redis down, **before fix** | Each Redis call hung **60 s**: location took 120 s to fail, sign-in 300 s, cancel 60 s | | | Threads exhausted |
| Redis down, **after fix** | Location fails in 4 s, sign-in in 10 s, cancel in 2 s | | | |
| OSRM down | | Fares | Straight-line estimate (by design) | None |
| Deploy | Old instance's in-flight requests (**before fix**) | | Graceful shutdown now set (P) | NOT YET MEASURED on Render; see 16 |

## N. Load-test results

Section B. The raw reports are in `D:\sheout-work\scale\results\` on the
test machine. The tools are `loadtest/sheout-load.mjs` (fixed here for more
than 600 accounts on Windows).

## O. Failure-test results

| Test | Result |
|---|---|
| 1. Instance stops mid-trip, another continues | **PASS** (17/17) |
| 2. Rider app crash, reopen | **PASS** |
| 3. Partner app crash, reopen | **PASS** |
| 4. Network gone and back | **PASS** (8 s offline, Playwright) |
| 5. Timeout, then retry | **PASS** after the fix. Duplicate booking was **FAIL** before. Complete and pay at once: one each. |
| 6. Traffic spike | Measured (B). First bottleneck: CPU (nearby-partner checks), then the `promotions` row lock, then the pool. |
| 7. Second instance, traffic distributed | **PASS** (even split, correct results) |
| 8. Rider on A, partner on B | **PASS** |
| 9. Database unavailable | **PASS** (no false success, recovers) |
| 10. Deploy during active trips | Covered by TEST 1, which is harsher: a hard kill. **NOT YET MEASURED on Render:** start a trip on staging, Manual Deploy, keep the trip running through the switch, and look for `Graceful shutdown complete` in the old instance's log. |

## P. Changes made

Each one fixes a problem shown above, and nothing else was redesigned.

1. **One booking per rider at a time across servers.** A Postgres advisory
   lock in `requestBooking` (`BookingRepository.lockNewBookingsFor`).
2. **Promotion lock held for milliseconds, not a dispatch round.** The
   discount is reserved after the search starts, still in the same
   transaction (`BookingService.requestBooking`).
3. **Preview eligibility cache, 15 s per partner, per instance.** Used for
   the nearby markers and quote ETA only (`DispatchService.isEligibleForPreview`).
4. **Redis command and connect timeouts of 2 s**, overridable with
   `REDIS_COMMAND_TIMEOUT` and `REDIS_CONNECT_TIMEOUT`.
5. **Graceful shutdown** (`server.shutdown: graceful`, 20 s), plus the
   notification queue drains for up to 15 s on shutdown.
6. **`GET /bookings/me?recent=N`.** Partner Home and the rider's live-trip
   checks use it. Without the parameter the response is unchanged, so
   installed apps and Earnings keep working.
7. **Rider app resumes a live trip on launch** (`LiveTripBanner`). There's
   also a "You are on your trip" card on Home, with strings in English,
   Hindi and Telugu (Hindi and Telugu need native-speaker review).
8. **Load tool:** SQL goes to `psql` on stdin, so 600+ accounts work on
   Windows.
9. **Admin console** (asked for separately):
   - each person's profile photo, or initials, in place of the bike and
     person glyphs
   - rider and partner photos side by side on bookings
   - an explicit "View profile ›", "View booking ›" or "Review ›" on every
     clickable row
   - a partner icon on the Live page

## Q. Files changed

- Backend:
  - `booking/internal/BookingRepository.java`
  - `booking/internal/BookingService.java`
  - `booking/internal/web/BookingController.java`
  - `dispatch/internal/DispatchService.java`
  - `notifications/internal/NotificationDeliveryConfig.java`
  - `admin/internal/{AccountOpsRow,BookingOpsRow,ReviewQueueRow,AdminService}.java`
  - `resources/application.yml`
  - `resources/static/admin/index.html`
- Rider app:
  - `components/LiveTripBanner.tsx` (new)
  - `screens/Home.tsx`
  - `lib/liveTrip.ts`
  - `api/client.ts`
  - `i18n/{en,hi,te}.json`
- Partner app: `screens/Home.tsx`, `api/client.ts`
- `loadtest/sheout-load.mjs`
- This document

No infrastructure was changed. Nothing was deployed.

## R. Remaining risks

1. **A single instance and a single region.** A crash is an outage until
   Render restarts it, though trips survive.
2. **Bursts above about 300 bookings a minute** on one Standard instance
   degrade badly. Above 600 they fail.
3. **Database connections grow with instances** (10 each). The limit on
   `sheout-db` is unverified.
4. **Notifications queued in memory are lost on a crash** (but drained on a
   deploy). A durable outbox would fix this. That's not justified yet.
5. **Every request costs a database round trip for the session check.**
   That's the next thing to cache when request volume, not bookings, is the
   limit.
6. **Backups.** The README describes the free plan, which has none.
   `sheout-db` is now paid, so point-in-time recovery should exist, but
   **a restore has never been tested.** RPO and RTO are NOT YET MEASURED
   (see S).
7. **The trip-start trail and the "arriving" alert** are started by
   after-commit listeners. If an instance dies in that instant, that trip
   gets no route check and no arriving push. The trip itself is unaffected.
8. **Graceful shutdown is configured but not yet seen working on Render**
   (TEST 10).

## S. Next steps, in order

**Before launch**
1. Deploy this change: backend, then both apps.
2. On `sheout-db`, run `SHOW max_connections;` and write the number down.
   Instances × 10 must stay well under it.
3. **Test a restore.**
   - Render → `sheout-db` → Recovery → restore to a new instance at "10
     minutes ago".
   - Connect to it and count the rows in `bookings` and `payments`.
   - Time it, then delete it.
   - That gives the real RPO and RTO. Update the README's backup section,
     which still describes the free plan.
4. Run TEST 10 on staging (a deploy during a trip). Look for `Graceful
   shutdown complete`.
5. Set up the uptime monitor (README, "Uptime monitoring").

**When traffic grows**
1. Before any planned busy evening, or once bookings regularly pass about
   200 a minute, set `sheout-backend` to **2 instances** (about $25 a month
   more) and check (2) above.
2. Run the two-instance staging test in B for real Render numbers.
3. Turn on `pg_stat_statements` and look at the top queries.
4. If request volume is the limit, cache the session check for a few
   seconds.
5. Then consider moving notifications to a durable outbox.

Nothing here needs sharding, a message broker, WebSockets or Kubernetes.
