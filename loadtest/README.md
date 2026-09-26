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

It writes `report.txt` (per-endpoint p50/p95/p99, errors, Redis command
rate, trip outcomes, per-minute latency) and `results.json`.

"Errors" counts network failures, timeouts (30 s), 5xx, 401 and 429 - and any
other 4xx the endpoint should not return. Business refusals the apps expect
(404 while a partner has not reported a position, 409 when another partner
claimed an offer first) are counted but not as errors.
