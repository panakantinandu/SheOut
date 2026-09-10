# SheOut

Women-only ride and delivery platform. Launching in **Hyderabad only**.

This repo started as a **scaffold** (folder structure, module boundaries,
local dev wiring) and now has two real modules implemented: `auth` and
`driver-verification`. Everything else is still just structure - see the
module table below.

## Stack

- **Backend:** Spring Boot 3 + Java 21 (Maven), one deployable app organized
  as a modular monolith.
- **Data:** PostgreSQL (primary store), Redis (cache / ephemeral state).
- **Frontend:** React + Vite, two separate PWAs - `customer-app` and
  `driver-app`.

## Architecture: modular monolith

One deployable app (`backend/`), internally split into modules by domain.
There are no microservices here, and there should not be until a module has
an actual, proven reason to be extracted.

### The module boundary rule

> A module talks to another module **only** through that module's public
> interface - never by importing another module's internal classes, entities,
> or repositories, and never by querying another module's database tables
> directly.

Concretely, each module is a top-level Java package under `com.sheout`:

```
com.sheout.<module>            <- public API: only classes here may be
                                   imported by other modules
com.sheout.<module>.internal   <- entities, repositories, internal services.
                                   Other modules must never import from here.
```

This is enforced by convention today (see each module's `package-info.java`).
If it starts getting violated, the next step is an ArchUnit test that fails
the build on a cross-module `internal` import - not a rewrite.

### Modules

| Module                 | Package                          | Owns |
|-------------------------|-----------------------------------|------|
| `shared-kernel`         | `com.sheout.sharedkernel`         | Cross-cutting types every module may depend on: `BaseEntity`, `Result`, `DomainEvent` / `DomainEventPublisher`, `ApiErrorResponse` / `ApiException` / `GlobalExceptionHandler`. Nothing domain-specific. |
| `auth` **(implemented)** | `com.sheout.auth`                 | Phone + OTP signup/login for customers, drivers, and admins; issues the access token; owns `CurrentAccountContext` (who's calling). See "Auth & driver-verification" below. |
| `users` **(implemented)** | `com.sheout.users`                 | Customer/driver profile data: name, emergency contacts, home/work addresses (customer); vehicle details, online status (driver). Not identity/verification. See "Users" below. |
| `driver-verification` **(implemented)** | `com.sheout.driververification`   | Gender verification (all accounts) + police verification (drivers only): document submission, admin review queue, `AccountVerified` event. See "Auth & driver-verification" below. |
| `booking` **(implemented)** | `com.sheout.booking`               | The RIDE/DELIVERY state machine, fare estimate, `GeoAddress` (pickup/drop coordinates). See "Booking" below. |
| `dispatch` **(implemented)** | `com.sheout.dispatch`              | Matches a REQUESTED booking to a nearby ONLINE driver via Redis geo + an offer/accept race. No public API (nothing calls into it yet) - only `com.sheout.dispatch.internal`, no Postgres tables. See "Dispatch" below. |
| `payments`              | `com.sheout.payments`              | Fare charging, refunds, driver payouts (Razorpay). |
| `notifications`         | `com.sheout.notifications`         | Push/SMS/email, triggered by domain events (Firebase). |
| `admin`                 | `com.sheout.admin`                 | Internal operator tooling, composes other modules' public APIs. |

`com.sheout.platform` is **not** a domain module - it's cross-cutting
technical infrastructure (currently just the health-check endpoint).

### Persistence

Business logic must never contain raw SQL or ORM queries directly.
Each module keeps persistence behind a **repository interface** owned by
that module (in its `internal` package, implemented with Spring Data JPA,
entities extending `sharedkernel.BaseEntity`). No repository, entity, or
`EntityManager` from one module should ever be visible to another.

### Cross-module reactions: domain events, not direct calls

When a write in one module is something another module might eventually
need to react to (e.g. "booking confirmed", "driver verified"), publish a
`DomainEvent` via `sharedkernel.event.DomainEventPublisher` instead of
calling into the other module directly. The current implementation
(`SpringDomainEventPublisher`) is in-process, backed by Spring's
`ApplicationEventPublisher` - listeners in other modules use a plain
`@EventListener`. Business code depends only on the `DomainEventPublisher`
interface, so this can be swapped for Kafka/SQS later without touching any
module's business logic.

## Auth & driver-verification

### Flow

1. **`POST /api/v1/auth/otp/request`** `{ phoneNumber, role }` - generates a
   6-digit code, stores it in Redis (TTL `OTP_TTL_SECONDS`, default 300s),
   delivers it via `OtpSender` (see below).
2. **`POST /api/v1/auth/otp/verify`** `{ phoneNumber, code, role }` -
   verifies the code. First time for a phone number: creates an `Account`
   with the given role and publishes `AccountRegistered`. Otherwise: logs
   into the existing account (the role must match, or the call fails).
   Either way, returns a signed access token.
3. `driver-verification` listens for `AccountRegistered` and creates a
   verification record (`gender_verification_status = PENDING`, plus
   `police_verification_status = PENDING` if the account is a DRIVER) -
   auth never calls into driver-verification directly.
4. **`POST /api/v1/driver-verification/documents`** (multipart, self-service,
   requires a token) - uploads the Aadhaar image via `DocumentStorage`,
   moves `gender_verification_status` to `UNDER_REVIEW`.
5. An admin moves it to `VERIFIED`/`REJECTED` via
   **`POST /api/v1/admin/verification/{accountId}/gender-review`**
   (`{ decision, reason }`). Drivers additionally need
   **`POST /api/v1/admin/verification/{accountId}/police-review`**
   (`{ decision }`, no submission step - purely admin-set).
   **`GET /api/v1/admin/verification/queue?status=UNDER_REVIEW`** lists
   pending records with the account's phone number attached.
6. Once an account is fully verified (customer: gender VERIFIED; driver:
   gender **and** police both VERIFIED), driver-verification publishes
   `AccountVerified(accountId, role)`. Nothing listens yet - booking/
   dispatch will, once built, to gate booking/going-online.

### Swap points

- **`OtpSender`** (`auth.internal.otp`) - only implementation today is
  `ConsoleOtpSender` (logs the code). See its Javadoc for why a literal
  `FirebaseOtpSender` wasn't built - Firebase Phone Auth is a client-driven
  flow, not a backend "send this code" API like MSG91/Twilio are. Wiring
  MSG91 or Twilio in behind this interface is a drop-in follow-up.
- **`DocumentStorage`** (`driververification.internal.storage`) -
  `LocalDiskDocumentStorage` (default, dev-only) or `S3DocumentStorage`
  (`DOCUMENT_STORAGE_PROVIDER=s3`), selected purely by config. Swapping
  Aadhaar review for a third-party KYC API later means adding a new
  implementation here (or replacing the manual-review call sites in
  `VerificationService` with a call to the KYC API) - the workflow/state
  machine doesn't change either way.

### Flagged assumptions

None of these were specified - each is a reasonable default, called out so
they're easy to revisit rather than discovered later:

- **One phone number = one account = one fixed role.** Someone wanting
  both a customer and a driver identity isn't supported - they'd need two
  phone numbers today.
- **Signup and login are the same endpoint.** Verifying a code for an
  unknown phone number creates the account; there's no separate signup step.
- **Session strategy: a single signed JWT, no refresh token, no
  revocation before expiry.** Chosen as the simplest fit for a stateless
  API with two PWA clients, specifically to avoid pulling in full Spring
  Security for one bearer-token check. See `JwtService`'s Javadoc.
- **`gender_verification_status`/the review queue's
  `SUBMITTED → UNDER_REVIEW → VERIFIED/REJECTED` state machine were
  described slightly differently and are treated as the same 4-state
  enum** (`VerificationStatus`), with document submission moving
  `PENDING`/`REJECTED` straight to `UNDER_REVIEW`. See `VerificationStatus`'s
  Javadoc.
- **"Verification completes" (triggering `AccountVerified`) means gender
  verification alone for a customer, but gender *and* police verification
  both `VERIFIED` for a driver** - since the spec says a driver can't go
  online on gender verification alone. No event is published on rejection
  (not asked for).
- **No admin account provisioning exists yet** - the `admin` module isn't
  built, so there's no way to create an ADMIN account through the API. To
  exercise the review endpoints today, manually set a row's `role` to
  `ADMIN` in the `accounts` table.
  **Security fix found during live testing:** `AuthController` originally
  trusted the client-supplied `role` on signup with no restriction at all -
  meaning any phone number could self-serve an ADMIN token by simply
  passing `role: "ADMIN"` to `/api/v1/auth/otp/request` /`/verify`. Caught
  by actually running the app and trying it, not by review. Fixed by
  rejecting `role: ADMIN` on both self-service signup endpoints
  (`AuthController.requireSelfServiceRole`) - ADMIN can now only be set by
  editing the database row directly, as this note always intended.
- **Self-service document upload only** - a caller can only submit a
  document for their own account, not on someone else's behalf.
- **No OTP attempt lockout or resend cooldown** - a wrong code can be
  retried until the code expires; nothing rate-limits `POST otp/request`.
- **`LocalDiskDocumentStorage.resolveUrl` returns a `file://` path, not
  something an admin can open over HTTP** - the "open the document" part
  of the admin review flow is real only against S3; no file-serving proxy
  endpoint was built for local disk (wasn't asked for).
- **Enforcement of "accounts cannot book/accept trips until VERIFIED"
  lives in booking/dispatch, which don't exist yet** - `VerificationApi`
  exposes the status for them to check once built; nothing enforces it end
  to end today.

## Users

Customer and driver profiles - `com.sheout.users`. Deliberately ignorant of
booking/dispatch; it only owns identity/profile data.

### Flow

1. `users` listens for auth's `AccountRegistered` and creates an empty
   profile row (`CustomerProfileEntity` or `DriverProfileEntity`
   depending on role) - a profile always exists by the time a client can
   call any endpoint here, same reasoning as driver-verification's own
   record creation.
2. **`GET`/`PUT /api/v1/users/customer/me`** - self-service profile
   read/update (name, home/work address). **`GET`/`POST`/`DELETE
   /api/v1/users/customer/me/emergency-contacts[/{id}]`** - manage the
   contact list.
3. **`GET`/`PUT /api/v1/users/driver/me`** - self-service profile
   read/update (name, vehicle type, registration number).
   **`POST /api/v1/users/driver/me/status`** `{ status: ONLINE|OFFLINE }`
   - the gated write: going ONLINE requires a **live** call to
     driver-verification's `VerificationApi.findByAccountId` confirming
     both `gender_verification_status` and `police_verification_status`
     are `VERIFIED`, checked at the moment of the request.
4. `users` also listens for driver-verification's `AccountVerified` and
   updates a cached `verified` flag on the relevant profile - exposed on
   `CustomerProfileSummary`/`DriverProfileSummary` for display, but **not**
   what the online-status gate checks (see flagged assumption below).
5. `phoneNumber` on both summary DTOs is composed from auth's `AuthApi` at
   read time - never stored in this module's own tables.
6. Emergency contacts are exposed via a dedicated `EmergencyContactsApi`
   (separate from `CustomerProfileApi`) specifically so the notifications
   module can depend on just that narrow read, not a customer's whole
   profile, once it's built.

### Flagged assumptions

- **Reconciling "subscribe to the `AccountVerified` event, don't poll"
  with "[online status] fetched via the [verification] interface"** - these
  two requirements point in different directions for the same decision.
  Implemented both, for different purposes: the event updates a cached
  `verified` flag (satisfies "don't poll", used for display), while the
  actual online-status gate re-checks live via `VerificationApi` at
  request time (satisfies "fetched via the interface", and is the safer
  choice for a decision this safety-critical - a stale cache saying
  VERIFIED when it no longer is would be a real problem, even though
  there's no "un-verify" flow today to make that concrete). Worth
  confirming this split is what you had in mind.
- **`VerificationApi` already exposed exactly the query needed** - I
  read driver-verification's current interface before starting;
  `findByAccountId` already returns both `genderVerificationStatus` and
  `policeVerificationStatus`, so nothing needed to be added there.
- **Home/work addresses are two plain free-text fields**, not a
  structured address (line1/city/pincode/coordinates) or an open-ended
  address list - matches the literal "home/work saved addresses" wording,
  but booking/dispatch will likely need more structure than a free-text
  string once they need to actually route somewhere.
- **Profiles are auto-created empty on `AccountRegistered`**, filled in
  later via the self-service `PUT` - not explicitly requested, but mirrors
  driver-verification's own precedent and avoids a "profile not found" gap
  for a brand-new account.
- **Emergency contact `relationship` is free text**, not a constrained
  enum (Mother/Spouse/Friend/...) - relationships are open-ended and
  nothing in the spec suggested validating against a fixed set.
- **No cap on the number of emergency contacts.**
- **Self-service only** - a caller only ever reads/writes their own
  profile/contacts (enforced via `CurrentAccountContext` + a role check);
  there's no admin-on-behalf-of editing endpoint.

## Booking

The RIDE/DELIVERY state machine - `com.sheout.booking`. Contains no
matching logic itself; dispatch (not built yet) is expected to subscribe
to `BookingRequested` and call `assignDriver`.

### GeoAddress - flagged back to you, as asked

Pickup/drop are `GeoAddress(label, lat, lng)` - resolved coordinates,
always, regardless of whether a booking was created from free text the
client geocoded or one of a customer's saved addresses. This is a new
module-owned value object, not a change to `users`.

**This means `users`' `CustomerProfileEntity.homeAddress`/`workAddress`
(built in the users-module pass) are still plain free-text strings, and
can't be used to prefill a booking's pickup/drop with real coordinates as
they stand today.** Per your instruction, `users` wasn't changed to match
in this pass - but it's a real follow-up: either `users` starts storing
saved addresses as `label + lat + lng` too, or something geocodes them on
read before they reach `booking`.

### Flow

1. `type` (RIDE/DELIVERY) is required, as specified. **Added, not
   explicitly requested:** a `category` field (BIKE/AUTO/CAB/PARCEL/LUNCHBOX)
   - the literal "a booking has" field list only named `type`, but a RIDE
   with no vehicle type (or DELIVERY with no parcel/lunchbox distinction)
   isn't enough for dispatch to match a driver or for fare calculation to
   price it. `category.expectedType()` is validated against `type` at
   creation (e.g. PARCEL can't be requested as RIDE).
2. **`requestBooking`** (via `POST /api/v1/bookings`, customer-only)
   checks the customer is gender-verified via driver-verification's
   `VerificationApi` (calling the interface, not re-implementing the
   check), prices the trip via `FareCalculator`, creates the booking in
   REQUESTED, and publishes `BookingRequested`.
3. **`assignDriver(bookingId, driverId)`** is `BookingApi`'s other method
   - the one dispatch calls once it has matched an eligible driver.
   REQUESTED -> MATCHED, publishes `BookingMatched`. Booking trusts the
   caller here entirely; it does not re-check the driver is online/verified
   itself (that's dispatch's/users' job).
4. Everything else a booking's participants do is self-service HTTP,
   internal to this module (not on `BookingApi` - dispatch/other modules
   never call these): `POST /api/v1/bookings/{id}/accept` (driver only,
   must be the assigned driver, MATCHED -> ACCEPTED),
   `.../start` (-> IN_PROGRESS), `.../complete` (-> COMPLETED, sets
   `finalFare = fareEstimate` - no real trip-distance tracking exists to
   base a different figure on), `.../cancel` (customer or the assigned
   driver, any pre-IN_PROGRESS state -> CANCELLED). Plus
   `GET /api/v1/bookings/me` and `GET /api/v1/bookings/{id}`.
5. Every transition goes through `BookingStateMachine` (internal) - the
   one place valid REQUESTED/MATCHED/ACCEPTED/IN_PROGRESS/COMPLETED/
   CANCELLED transitions are decided, returning `Result` rather than
   throwing on an invalid one.
6. `FareCalculator` is a swap-point interface; `DistanceBasedFareCalculator`
   is a placeholder (straight-line/haversine distance, illustrative
   base+per-km rates loosely reverse-engineered from the one data point in
   the UI mockup - not a real pricing spec). Swapping in real routing
   distance or surge pricing later only means changing this implementation.

### Flagged assumptions

- **`category` field added** beyond the literal spec - see above.
- **Two extra events added: `BookingMatched` and `BookingStarted`.** The
  spec named `BookingRequested`/`BookingAccepted`/`BookingCompleted`/
  `BookingCancelled` but also said "emit domain events at each state
  transition" - REQUESTED->MATCHED and ACCEPTED->IN_PROGRESS have no named
  event, so these were added to honor that general rule literally. Drop
  them if only the 4 named events were intended.
- **Event payloads are minimal/purpose-specific** (bookingId + the few
  fields a plausible subscriber needs), not a full `BookingSummary` -
  following the same precedent as `AccountRegistered`/`AccountVerified`
  from earlier passes, since no exact shape was specified for any of these.
- **`BookingError` is public** (unlike other modules' internal `*Error`
  enums) - `BookingApi.assignDriver` is a real cross-module method, and its
  caller (dispatch) needs to be able to interpret what comes back.
- **Self-service endpoints beyond `requestBooking`/`assignDriver` were
  built** (accept/start/complete/cancel/list/get) - the spec said the
  module "only exposes" those two, which I read as scoping the
  cross-module *Java interface* (`BookingApi`), not ruling out HTTP
  self-service triggers for the other transitions - without them, MATCHED,
  ACCEPTED, IN_PROGRESS, COMPLETED, and CANCELLED would be unreachable
  from outside a unit test.
- **`assignDriver` does not re-verify the driver** is online/verified -
  booking has no dependency on `users` or a driver-side verification
  check; it trusts dispatch (once built) to only ever call this with an
  eligible driver.
- **No cancellation reason/initiator field** on `BookingCancelled` or the
  cancel endpoint - not specified, though a real system would likely want
  one for cancellation-fee logic.
- **`finalFare` always equals `fareEstimate`** at completion - there's no
  real distance/time tracking during a trip yet to base a genuinely
  different number on; flagged rather than faked.

## Dispatch

Matches a REQUESTED booking to a nearby ONLINE driver - `com.sheout.dispatch`.
No public Java API today: nothing else calls into dispatch, it only calls
out (to `BookingApi` and `DriverProfileApi`). No Postgres table either -
per the instructions, essentially all of this module's state (driver
locations, pending offers, race claims, retry-round bookkeeping) lives in
Redis and is ephemeral by design, so there's no `V4__...sql` migration
this pass.

### The most important thing to flag: `assignDriver` vs. booking's ACCEPTED state

**Dispatch's "a driver accepts the offer" and booking's ACCEPTED status are
not the same event, and this pass does not bridge them.** Concretely:

- `BookingApi` only exposes `requestBooking` and `assignDriver` -
  `assignDriver` performs booking's REQUESTED -> **MATCHED** transition
  (confirmed by reading `booking`'s state machine before starting this
  pass). It does not, and cannot, reach MATCHED -> ACCEPTED - that
  transition lives entirely inside `booking`'s own internal
  `acceptBooking`, reachable only via *booking's own*
  `POST /api/v1/bookings/{id}/accept` self-service endpoint (driver-only,
  checked against `CurrentAccountContext`), which is not part of
  `BookingApi` and which dispatch has no way to call without reaching into
  booking's internals - exactly what module boundaries forbid.
- So when a driver "accepts" one of *dispatch's* offers (via
  `POST /api/v1/dispatch/offers/{bookingId}/accept`, built in this pass),
  all that happens is: dispatch resolves its own internal accept-race,
  then calls `BookingApi.assignDriver` - the booking becomes MATCHED, not
  ACCEPTED.
- For the booking to actually reach ACCEPTED, the driver's app has to
  *separately* call booking's own `/accept` endpoint afterward. Nothing in
  this pass automates or triggers that second call - it wasn't something
  dispatch's public interface access could reach, and I didn't want to
  silently paper over the gap by having dispatch reach into booking's
  internals to force it.

This may be exactly the intended shape (dispatch's offer-race decides
*who*, booking's own ACCEPTED step is the driver's own formal
confirmation, matching the "New Request... Accept/Decline" screen in the
UI mockup) - or it may be an unintended seam between the booking and
dispatch passes. Worth explicitly confirming which, since as it stands a
booking dispatch "successfully" matched can sit in MATCHED indefinitely if
nothing ever calls booking's `/accept`.

### Flow

1. **Location updates**: `POST /api/v1/dispatch/location` (driver-only,
   self-service) `{ lat, lng }` - stored via Redis `GEOADD` (through
   `DriverLocationStore`, the one class in this module that touches Redis
   geo commands - see its Javadoc for why it's kept this narrow). Reuses
   `booking.GeoAddress`'s label+lat+lng *shape* conceptually, but the raw
   geo-set entries themselves are just lat/lng + driverId, as specified -
   no label is stored in Redis.
2. **On `BookingRequested`** (subscribed via `@EventListener`, not
   polled): runs an offer round - `GEOSEARCH` for nearby drivers, filter
   to ONLINE + right vehicle type (see flagged assumption below), rank
   via `MatchingStrategy` (nearest-first placeholder, swappable), and open
   a short-lived Redis-backed offer for the top N.
3. **"Broadcast"** (see flagged assumption below) - a driver discovers a
   pending offer via `GET /api/v1/dispatch/offers/me` (poll), and responds
   via `POST /api/v1/dispatch/offers/{bookingId}/accept` or `.../decline`.
4. **Accept** re-checks the driver is still ONLINE live (the race the spec
   calls out explicitly - a driver can go offline between being selected
   and accepting), then atomically claims the booking (Redis `SET ... NX`
   - first accept wins, everyone else's subsequent accept attempt fails
   the claim and gets `BOOKING_ALREADY_ASSIGNED`), then calls
   `BookingApi.assignDriver`.
5. **No acceptance within the window** (`offer-window-seconds`, default
   15 as suggested): a scheduled sweep (`DispatchRetrySweeper`, every
   `sweep-interval-ms`) finds the expired round and retries with an
   expanded radius (`radius-expansion-factor`), excluding every driver
   already offered this booking in an earlier round (`declined` also adds
   to this exclusion set) - combining the spec's "expanded radius **or**
   next-ring drivers" into one retry strategy rather than treating them as
   alternatives. After `max-retries` retries with no acceptance, the round
   is abandoned and the booking is left REQUESTED, per spec.

### Flagged assumptions

- **The `assignDriver`/ACCEPTED gap above** - the most significant one.
- **"Broadcast" is poll-based, not push.** No notifications module (no
  FCM) exists yet, so there's no way to deliver an offer to a driver's
  phone instantly. This pass provides the backend race/retry logic and an
  HTTP surface a driver app *could* poll; real-time delivery is a
  follow-up once notifications exists.
- **Vehicle-type/category eligibility filtering was added.** Only
  "nearest N... ONLINE" was specified; filtering so a CAB booking isn't
  offered to a BIKE driver felt like basic eligibility (same category as
  the required ONLINE check) rather than the "smarter scoring" explicitly
  deferred to a future `MatchingStrategy`. PARCEL/LUNCHBOX are matched to
  BIKE drivers specifically - a guess, not derived from any spec.
- **A decline endpoint was added** (`POST .../offers/{bookingId}/decline`)
  beyond what was asked, matching the mockup's Accept/Decline screen -
  removes that driver's offer and excludes them from later rounds for
  this booking, but does not trigger an early retry (the round still
  resolves on the normal timeout).
- **All retry/timeout defaults are guesses**, beyond the offer window's
  "e.g. 15 seconds" (used as the actual default): initial radius 3km,
  radius expansion ×2 per retry, 5 candidates per round, 3 retries, a
  2-second sweep interval. All configurable, none tuned against anything
  real - see `sheout.dispatch.*` in `application.yml` / `.env.example`.
- **A recurring scheduled sweep, not a one-off delayed task per booking** -
  chosen so a retry-in-progress survives an app restart (state is
  re-derived from Redis on every tick) rather than being silently lost.
  Given this module was explicitly flagged as a likely first candidate to
  become its own service, that seemed worth a small polling interval.
- **No staleness/heartbeat expiry on driver locations** - a driver whose
  app disappears without explicitly going OFFLINE (via `users`' own
  status endpoint) leaves a stale `GEOADD` entry that lingers
  indefinitely; the ONLINE check via `DriverProfileApi` is what's relied
  on for eligibility, not location freshness. A real system would likely
  want a TTL'd heartbeat; not built since not specified.
- **`DriverLocationStore` is the one file in this pass I could not verify
  compiles.** It uses Spring Data Redis's GEOSEARCH-era API
  (`GeoReference`/`GeoShape`/`GeoSearchCommandArgs`), which is less common
  than the older `geoRadius()` method most examples online still show -
  I used my best understanding of the exact class locations/method names,
  but with no Maven available to build against (same limitation as every
  backend pass this session), this is the single highest-risk file to
  check first.

## Local development

### Prerequisites

- JDK 21
- Maven (or use the Docker build, which doesn't need Maven installed locally)
- Node.js 20+
- Docker Desktop (for `docker-compose`)

### Run everything with Docker Compose

```bash
cp .env.example .env   # fill in real values only if/when you have them
docker compose up --build
```

This starts the Spring Boot app on `:8080`, Postgres on `:5432`, and Redis
on `:6379`. Check it's alive:

```bash
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/actuator/health
```

### Run the backend alone (no Docker)

Needs a local Postgres + Redis (or point `DB_HOST`/`REDIS_HOST` env vars at
hosted ones - see below).

```bash
cd backend
mvn spring-boot:run
```

### Run a frontend app

```bash
cd frontend/customer-app   # or frontend/driver-app
npm install
npm run dev
```

`customer-app` runs on `:5173`, `driver-app` on `:5174`.

## Configuration & secrets

All config is environment-variable driven (`application.yml` /
`application-local.yml` under `backend/src/main/resources/` read
`${VAR}` / `${VAR:default}` everywhere). **No secret is ever committed.**

There are two profiles:

- **`local`** (active in docker-compose, and by convention for
  `mvn spring-boot:run`) - builds Postgres/Redis connections from discrete
  `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` /
  `REDIS_HOST` / `REDIS_PORT` vars, all defaulted for docker-compose's own
  Postgres/Redis containers. See `.env.example`.
- **default** (active everywhere else, i.e. Render) - requires
  `DATABASE_URL` and `REDIS_URL` as full connection strings, no default,
  so a missing one fails startup loudly instead of quietly reaching for
  `localhost`. See "Deploying to Render" below for the exact format each
  needs.

`auth` and `driver-verification` config lives under `sheout.auth.*` /
`sheout.document-storage.*` in `application.yml`, same env-var-driven
pattern - `JWT_SECRET` (required on Render, dev-only default in
`application-local.yml`), `JWT_EXPIRY_MINUTES`, `OTP_TTL_SECONDS`,
`DOCUMENT_STORAGE_PROVIDER` (`local`/`s3`) plus its `local.path` or
`s3.*` settings. S3 credentials are read via the AWS SDK's own
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, not a SheOut-specific var.
See `.env.example` for the full list.

`RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` / `FIREBASE_PROJECT_ID` /
`FIREBASE_CREDENTIALS_JSON` are placeholders in both profiles - payments
and notifications aren't implemented yet.

Copy `.env.example` to `.env` (gitignored) for local dev and fill in
values as each integration gets implemented.

## Deploying to Vercel

`customer-app` and `driver-app` are live:

- **customer-app:** https://sheout-customer-app.vercel.app
- **driver-app:** https://sheout-driver-app.vercel.app

Both are separate Vercel projects (`sheout-customer-app`, `sheout-driver-app`
under the `panakantinandus-projects` team) watching the same GitHub repo.

### Why there's no committed `vercel.json`

This repo is an npm-workspaces monorepo - `@sheout/design-system` doesn't
exist on the npm registry, so `npm install` has to run at the **repo
root** (where the `workspaces` field lives), not inside
`frontend/customer-app`/`frontend/driver-app` directly, or it 404s trying
to fetch the design-system package. That means each project's install/
build/output settings need to point at the root install + the right
per-app build script and output folder.

The catch used to be that both Vercel projects watch the *same* repo, so a
single committed `vercel.json` **at the repo root** would apply to both
projects' builds - correct for one app, wrong for the other. A root
`vercel.json` is therefore still gitignored.

That is resolved now: each project has its **Root Directory** set to its
own app folder (Project Settings > General > Root Directory:
`frontend/customer-app` or `frontend/driver-app`). Vercel's monorepo
detection handles the root-level install from there, and a `vercel.json`
placed *inside each app folder* applies to exactly one project. Those two
per-app files **are committed** and must stay that way.

**They are not optional.** Vercel's Vite preset does not add an SPA
catch-all rewrite, so without `frontend/<app>/vercel.json` every deep link
- `/login`, `/home`, a shared tracking URL - returns a hard 404 to anyone
visiting it directly. This is easy to miss, because the PWA service worker
serves `index.html` from cache for anyone who has already opened the app
once; only first-time visitors and `curl` see the 404.

### VITE_API_BASE_URL

`client.ts` reads `VITE_API_BASE_URL`, falling back to
`http://localhost:8080` for local dev. It is now set to
`https://sheout-backend.onrender.com` on **both** Vercel projects.

The fallback uses `||`, not `??`, on purpose. This variable was once
stored as an empty string, and `??` only falls back on null/undefined - so
`""` passed straight through, `API_BASE` became `""`, and every request
resolved against the app's own origin instead of the backend. An empty
value means "not configured" here and must fall back like a missing one.

The backend's
`CORS_ALLOWED_ORIGINS` also needs the deployed frontend origin
(`https://sheout-customer-app.vercel.app`) added, or every request will
fail CORS the same way local dev did before `WebConfig` was added.

## Deploying to Render

The backend, its Postgres database, and its Redis (Render calls this
"Key Value") instance all deploy together from `render.yaml` at the repo
root (a Render "Blueprint") - one **New > Blueprint**, pick this repo,
Apply, and all three resources come up together. Render builds the web
service directly from `backend/Dockerfile`.

**This is a deliberate choice, not the only option**: Render's free
Postgres expires after 30 days (the database is deleted, not just
paused - you'd recreate it and rerun migrations) and its free Key Value
caps at 25MB. That's an accepted trade-off for now in exchange for zero
external accounts to manage; move both resources' `plan:` in
`render.yaml` to a paid tier before that 30-day clock matters, or before
`dispatch`'s live driver-location data outgrows 25MB.

### Connecting the repo

1. Push this repo to GitHub.
2. In the Render dashboard: **New > Blueprint**, connect your GitHub
   account if prompted, then pick this repo. Render reads `render.yaml`
   and proposes three resources: `sheout-db` (Postgres), `sheout-redis`
   (Key Value), and `sheout-backend` (the web service).
3. Render will prompt for the env vars marked `sync: false` -
   **`JWT_SECRET`** (any strong random value, e.g. `openssl rand -base64 48`)
   is the only one required for startup; `RAZORPAY_KEY_ID`,
   `RAZORPAY_KEY_SECRET`, `FIREBASE_PROJECT_ID`, and
   `FIREBASE_CREDENTIALS_JSON` can stay blank until those integrations
   exist. `DATABASE_URL` and `REDIS_URL` are **not** prompted for - they're
   wired automatically from `sheout-db`/`sheout-redis` via `fromDatabase`/
   `fromService` in `render.yaml`, not something you paste in yourself.
4. Click **Apply**. First build (Docker image, pinned to Java 21) takes a
   few minutes; watch for `Started SheOutApplication` and Flyway applying
   its 3 migrations in the logs, same as a local run.
5. Every subsequent push to the connected branch auto-deploys.

**Document storage on Render:** not declared in `render.yaml`, so it
defaults to `LocalDiskDocumentStorage` - writing to Render's container
filesystem, which is wiped on every restart/redeploy. That's fine while
no real Aadhaar documents are being collected, but before that changes,
set `DOCUMENT_STORAGE_PROVIDER=s3` plus `DOCUMENT_STORAGE_S3_BUCKET`,
`AWS_ACCESS_KEY_ID`, and `AWS_SECRET_ACCESS_KEY` in the dashboard so
uploads actually persist.

### What comes from render.yaml vs. the dashboard

- **`render.yaml`** owns everything that isn't a secret: the Postgres and
  Key Value resources themselves, which Dockerfile to build, the health
  check path, the region, the plan tier, and the *names* of the required
  env vars (including that `DATABASE_URL`/`REDIS_URL` come from those
  resources automatically).
- **The dashboard** owns the actual *values* of every `sync: false` var -
  just `JWT_SECRET` and the not-yet-used Razorpay/Firebase placeholders.
  None of these are ever written to this file or committed.

### Why DATABASE_URL doesn't need manual reshaping (on Render, or anywhere else)

Render hands back `DATABASE_URL` as `postgres://user:pass@host:port/db` -
the standard connection-string form basically every Postgres provider
uses (Neon, Supabase, AWS RDS included). Spring's JDBC driver can't
consume that directly (pgjdbc doesn't support `user:pass@` in the URL at
all), so historically this needed hand-editing into
`jdbc:postgresql://host:port/db?user=...&password=...` before pasting it
in.

That's now automatic: `PostgresUrlEnvironmentPostProcessor`
(`com.sheout.platform`) rewrites `DATABASE_URL` into the JDBC form at
startup, for *any* provider that hands back the standard form - not
Render-specific. If you ever point `DATABASE_URL` at Neon, Supabase, or
RDS instead, paste their connection string in as-is; no manual reshaping
needed there either anymore. `REDIS_URL` never needed this - Spring Data
Redis's URL parser already handles `redis://user:pass@host:port` natively.

### Free tier trade-off

Render's free plan spins the service down after ~15 minutes of no
traffic; the next request pays a ~30-60s cold start while it spins back
up. That's fine for early scaffolding and internal poking around, but
**this must move to a paid Starter instance (`plan: starter` in
render.yaml) before any real user or driver testing that involves live
booking, tracking, or SOS - not just before "launch."** A 30-60s hang on
an SOS call is not an acceptable trade-off at any pre-launch stage.

## What's intentionally not here yet

- Any business logic in `payments`, `notifications`, or `admin` - only
  `auth`, `driver-verification`, `users`, `booking`, and `dispatch` are
  implemented (see their sections above, including flagged-assumptions
  lists).
- A bridge from dispatch's own offer-accept race to booking's ACCEPTED
  status (only as far as MATCHED) - see "Dispatch" above's flagged gap.
- Real-time offer delivery to a driver's phone (push/FCM) - dispatch's
  offers are poll-only until `notifications` exists.
- `booking` reading `users` at all, or vice versa - neither depends on the
  other. `dispatch` is what composes both, via `DriverProfileApi` and
  `BookingApi`.
- `users`' saved addresses using the `GeoAddress` shape `booking`
  introduced - see "Booking" above's flagged note.
- Real PWA icons (`vite.config.ts` in both frontend apps references
  `/icons/icon-192.png` and `/icons/icon-512.png` as placeholders).
- CI/CD.
- An embedded/Testcontainers substitute for backend tests - the one
  context-load test needs a real local Postgres + Redis running (e.g.
  `docker compose up postgres redis`) to pass.
