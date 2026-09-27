# Load test

`sheout-load.mjs` simulates a busy launch evening: riders booking and
riding, partners online and driving, every interval taken from the apps'
own code. No dependencies beyond Node 20+.

**Never run it against production.** It signs in with fixed test codes and
marks its accounts verified directly in the database. It refuses the
production hostnames.

## What each simulated person does

Partner (default 40): goes online near central Hyderabad; sends her location
every 7 s, moving at ~25 km/h toward her pickup or drop; polls for offers
every 4 s; refreshes her trip list and payment hold every 5 s; claims an
offer after 1-5 s, accepts, drives to the pickup, starts with the rider's
code, drives to the drop and ends the trip.

Rider (default 100, all booking within the first minute): opens the booking
screen (nearby partners, fare quote), books, polls the booking every 3 s,
her partner's position every 7 s once accepted, reads the pickup code, pays
from her wallet when the trip ends, waits 1-3 minutes, and books again.

Dispatch, both geofences, the pickup code, OSRM and the wallet all run for
real.

## Environment

A staging backend started with:

| variable | value | why |
|---|---|---|
| `DEV_OTP_NUMBERS` | `+919700000000..0199` and `+919600000000..0199`, each `:246810` | the test accounts' sign-in |
| `LOGIN_PER_CLIENT` | `100000` | every simulated person comes from one machine; the per-network sign-in limit would stop the setup |
| `OSRM_BASE_URL` | the staging OSRM | measure our own router, not the public one |

and the same database/Redis the backend uses, reachable with `psql` and
`redis-cli` from where the script runs (`--psql`, `--redis-cli`,
`--redis-port`).

    node sheout-load.mjs --api http://STAGING:8080 --minutes 15 --riders 100 --partners 40 --out results/

Against the Render staging service (`sheout-backend-staging`, which has
`DEV_OTP_TEST_PREFIX=+91999999` and `LOGIN_PER_CLIENT` set in render.yaml),
with staging's external database URL from the Render dashboard:

    node sheout-load.mjs --api https://sheout-backend-staging.onrender.com \
      --code 123456 --rider-prefix +9199999970 --partner-prefix +9199999960 \
      --db-url "$LOAD_DB_URL" --no-redis --minutes 15 --riders 100 --partners 40 --out results/

Staging's Redis is internal-only, so `--no-redis` skips its sampling. Without
the database URL, `--no-db` reuses accounts an earlier run seeded and clears
that run's unfinished trips through the API instead. In both cases
server memory and CPU come from the service's Metrics tab rather than this
script. The report measures the network round trip before the run; every
latency includes it.

It writes `report.txt` (per-endpoint p50/p95/p99, errors, Redis command
rate, trip outcomes, per-minute latency) and `results.json`.

"Errors" counts network failures, timeouts (30 s), 5xx, 401 and 429 - and any
other 4xx the endpoint should not return. Business refusals the apps expect
(404 while a partner has not reported a position, 409 when another partner
claimed an offer first) are counted but not as errors.

## Results: staging, 2026-09-27

`sheout-backend-staging` on Render Starter (0.5 CPU, 512 MB, the same instance
as production), its own OSRM, Postgres and Key Value, all in Singapore. The
client ran from Kansas City, so every figure below includes a ~245 ms round
trip (measured before each run); the server's own share is roughly the figure
minus 245 ms. 100 riders, all booking within the first minute and riding
again and again; 40 partners sending their location every 7 s; 16 minutes.

| | run 1: server just redeployed (cold) | run 2: warm |
|---|---|---|
| requests | 43,269 (45.1/s) | 44,851 (46.7/s) |
| errors | 1 (0.002%): one nearby-partners call timed out at 30 s, minute 2 | 0 |
| all requests p50 / p95 / p99 | 251 / 881 / 4,658 ms | 248 / 407 / 692 ms |
| create booking p95 / p99 | 12,725 / 24,716 ms | 1,657 / 2,600 ms |
| nearby partners p95 / p99 | 22,869 / 27,605 ms | 1,413 / 2,323 ms |
| partner location p95 | 849 ms | 365 ms |
| p95 by minute | 5.4 s and 5.7 s in minutes 1-2, then 369-474 ms | 708 ms in minute 1, then 359-450 ms |
| trips booked / accepted / completed | 347 / 74 / 34 | 357 / 76 / 36 |

"No partner available" (254 and 263) is supply, not failure: from the second
minute on, 37-40 of the 40 partners were already on a trip (booking table,
per minute). Trips run at a simulated 25 km/h, so most take longer than a few
minutes and many were still under way when the run ended.

What it shows: a warm Starter instance carries this load with no errors and
a steady p95 of about 400 ms from here (~150 ms at the server). A burst of
bookings in the first minutes after a deploy, while the JVM is still cold,
is slow: deploy outside busy hours.

Not measured here: Redis (staging's is internal-only) and the server's CPU
and memory, which are in the service's Metrics tab on Render.

## Cold start, 2026-09-27

Why a burst right after a deploy was slow, and what fixed it. Local backend
pinned to one CPU core (Render Starter has half of one), restarted cold, then
straight into the same load: 100 riders booking in the first minute, 40
partners. Each row is one cold start.

| JVM | startup | 1st-minute p95 | booking p95 | nearby p95 | quote p95 |
|---|---|---|---|---|---|
| as deployed before: default heap (128 MB), default compilers | 37 s | 2,722 ms | 7,613 | 7,946 | 4,598 |
| heap 300 MB | 27 s | 1,610 ms | 4,435 | 2,703 | 1,419 |
| heap 300 MB + warm-up, 3 rounds | 18 s | 1,592 ms | 6,183 | 2,502 | 2,057 |
| heap 300 MB + warm-up, 40 rounds (11 s) | 16 s | 1,968 ms | 7,326 | 5,875 | 2,446 |
| **heap 300 MB + first-tier compiler only (C1)** | 15 s | **373 ms** | **301** | **142** | **220** |
| same server again, warm (default compilers) | - | 514 ms | 109 | 83 | 123 |
| same server again, warm (C1 only) | - | 275 ms | 468 | 215 | 269 |

The cold cost is the optimising compiler: until code has run thousands of
times it is interpreted, and compiling it competes with the requests for
the one CPU. A warm-up of synthetic requests cannot pay that off - it cannot
run the real signed-in paths or dispatch without side effects - so it stays
small and is there for one-off costs (pools, first queries, first router
call), behind the readiness check. Stopping at the first-tier compiler
removes the spike, at the cost of slower warm peak on heavy calls, and uses
~90 MB less memory (peak working set 400 MB against ~490 MB).

Class-data sharing (the archive built in the Dockerfile) then takes startup
from 13-14 s to 9.5 s on one core.

Confirmed on staging (Render Starter, half a CPU), 27 Sep 03:22-03:40 UTC,
straight after deploying 4da0946 - the same 16-minute run as "run 1: cold"
above:

| | run 1: cold, before | run 3: cold, heap + C1 + CDS + warm-up |
|---|---|---|
| errors | 1 of 43,269 | 0 of 44,557 |
| create booking p95 / p99 | 12,725 / 24,716 ms | 2,100 / 3,502 ms |
| nearby partners p95 | 22,869 ms | 3,497 ms |
| p95, minute 1 / minute 2 | 5,444 / 5,667 ms | 1,685 / 566 ms |
| all requests p95 | 881 ms | 481 ms (warm run 2: 407 ms) |
| p95, minutes 3-16 | 369-474 ms | 397-569 ms |

Every figure includes the ~255 ms round trip from Kansas City.

Run 4, staging redeployed just before (cold), 27 Sep, load 05:36:28-05:52:28
UTC, `--no-db` (accounts from earlier runs; their leftovers cleared through
the API): 44,885 requests, 0 errors, p95 415 ms overall, create booking p95
1,812 ms, nearby partners p95 2,094 ms, first-minute p95 1,020 ms, minutes
2-16 at 305-501 ms. Peak memory for the window: see the service's Metrics.
Metrics for that window (read by the operator): memory limit 512 MB and CPU
limit 0.5 - staging was on Starter at the time, not Standard - memory flat
around 400 MB with no restart, CPU peak about 0.4.

Memory headroom, closed: this workload needs ~400 MB in all on a 512 MB
instance, where the heap is capped at ~307 MB (60%) and everything else takes
~100 MB. On production's Standard (2 GB) the heap cap is ~1.2 GB, so the JVM
cannot exceed roughly 1.35 GB (~70% of the container) whatever the load, and
the same load used 0.4 of half a CPU against Standard's full one. No separate
2 GB load test is needed; production's own Metrics under real traffic will
give the real figure.
