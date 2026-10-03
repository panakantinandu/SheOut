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
| `driver-verification` **(implemented)** | `com.sheout.driververification`   | Gender verification (all accounts) + police verification (drivers only): document submission, admin review queue, `AccountVerified` event. A partner's licence, vehicle papers and their expiry, and the one "may she take trips now" answer (`partnerReadiness`). See "Auth & driver-verification" and "Partner documents and expiry" below. |
| `booking` **(implemented)** | `com.sheout.booking`               | The RIDE/DELIVERY state machine, fare estimate, `GeoAddress` (pickup/drop coordinates). See "Booking" below. |
| `dispatch` **(implemented)** | `com.sheout.dispatch`              | Matches a REQUESTED booking to a nearby ONLINE driver via Redis geo + an offer/accept race. No public API (nothing calls into it yet) - only `com.sheout.dispatch.internal`, no Postgres tables. See "Dispatch" below. |
| `payments` **(implemented)** | `com.sheout.payments`              | Charging a trip's fare: Razorpay order at completion, Checkout verification, webhook capture, cash confirmed by the partner. The single capture path publishes `PaymentCaptured`. |
| `payouts` **(implemented)** | `com.sheout.payouts`               | What a partner is owed and how she is paid: wallet (credited from `PaymentCaptured`, never polled), bank/UPI payout details, payout requests. Payouts are sent by hand and marked paid in the console - no payout API. |
| `notifications` **(implemented)** | `com.sheout.notifications`         | The in-app inbox, push (Firebase Cloud Messaging), SMS (Twilio) and email (SMTP), all triggered by domain events; SOS alerts to emergency contacts and operators. |
| `insurance` **(implemented)** | `com.sheout.insurance`        | Master policies (entered in the console), each trip's cover opened and closed from booking's events, the daily bordereau, premiums as a platform cost, partners' group-cover enrolment, and accident reports/claims. See "Insurance" below. |
| `admin`                 | `com.sheout.admin`                 | Internal operator tooling, composes other modules' public APIs. |
| `staff` **(implemented)** | `com.sheout.staff`               | The people who run the console: email + password + authenticator sign-in, invitations, cookie sessions, the role/permission catalogue every console endpoint is checked against. See "Staff accounts and roles" below. |

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
   **`POST /api/v1/admin/verification/{accountId}/police-review`** - since
   V56 a decision with its evidence, not a bare decision: see "Police
   verification with evidence" below.
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
- **`DocumentStorage`** (`sharedkernel.storage`) - `DatabaseDocumentStorage`
  (default: stored in Postgres, served through signed expiring links),
  `S3DocumentStorage` (`DOCUMENT_STORAGE_PROVIDER=s3`) or
  `LocalDiskDocumentStorage` (`local`, never on Render - its disk is wiped on
  every deploy), selected purely by config. Swapping
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
- **Operators are staff accounts now, not phone accounts.** See "Staff
  accounts and roles". ADMIN phone-and-code sign-in is refused while
  `ADMIN_PHONE_LOGIN_ENABLED` is off (production), and an ADMIN account is
  created only by the staff module, for someone who accepted an invitation.
  **Security fix found during live testing (earlier):** `AuthController` originally
  trusted the client-supplied `role` on signup with no restriction at all -
  meaning any phone number could self-serve an ADMIN token by simply
  passing `role: "ADMIN"` to `/api/v1/auth/otp/request` /`/verify`. Caught
  by actually running the app and trying it, not by review. Fixed by
  rejecting `role: ADMIN` on both self-service signup endpoints
  (`AuthController.requireSelfServiceRole`) - ADMIN can now only be set by
  editing the database row directly, as this note always intended.
- **Self-service document upload only** - a caller can only submit a
  document for their own account, not on someone else's behalf.
- **OTP limits** (this note used to say there were none): a code is
  destroyed after 5 wrong guesses, and both `otp/request` and `otp/verify`
  answer `429` with `Retry-After` past 5 per phone number per 15 minutes,
  plus a 45s resend cooldown, an hourly request cap, and a looser per-client
  limit. See `OtpRateLimiter`, `sharedkernel.ratelimit` and
  `sheout.rate-limit.*` in `application.yml`. Per-client limits need
  `CLIENT_IP_HEADER` set behind a proxy - see `ClientAddressResolver`.
- **`LocalDiskDocumentStorage.resolveUrl` returns a `file://` path, not
  something an admin can open over HTTP** - the "open the document" part
  of the admin review flow is real only against S3; no file-serving proxy
  endpoint was built for local disk (wasn't asked for).
- **Enforcement of "accounts cannot book/accept trips until VERIFIED"
  lives in booking/dispatch, which don't exist yet** - `VerificationApi`
  exposes the status for them to check once built; nothing enforces it end
  to end today.

## Staff accounts and roles

The ops console can see every woman's identity documents, police
certificates, addresses, live location, phone number and payment details,
and it can block accounts and mark payouts paid. So the people who use it
are **staff accounts** (the `staff` module), not riders with a role flag:
one stolen login must never expose everything or move money. The plain-words
version for the founder is [docs/ADMIN_SECURITY.md](docs/ADMIN_SECURITY.md).

Phases 1 (accounts, sign-in, permissions) and 2 (masking, step-up, scoping,
audit, alerts) are built. What is not built yet is listed at the end.

### Roles

Roles are fixed in code (`staff.StaffRole`), never edited from the console.
Code checks **permissions**, never role names; the full matrix is
[`backend/src/test/resources/staff/permission-matrix.txt`](backend/src/test/resources/staff/permission-matrix.txt),
and `PermissionMatrixTest` fails if a role changes without that file
changing in the same commit.

| Role | Who | Can | Cannot |
|---|---|---|---|
| `OWNER` | Founders, 1-2 people | Everything, including staff, configuration, insurance, campaigns, marking payouts paid | Disable or demote themselves; the last active owner cannot be disabled or demoted |
| `MANAGER` | Operations head | Every queue, verification, SOS and safety, block/unblock, ops and finance reports, service hours, content, announcements; invite/disable/re-role employees | Create managers or owners, re-enable anyone, change insurance, campaigns or system settings, mark payouts paid |
| `VERIFICATION_AGENT` | Onboarding | IDs, selfies, partner documents, police evidence | Payments, payouts, trips, riders' accounts |
| `SUPPORT_AGENT` | Customer care | Support tickets | Documents, police certificates, trips outside tickets |
| `SAFETY_RESPONDER` | 24x7 safety desk | SOS alerts, trip alerts, route reviews | Payments, documents, the live map of all trips |
| `FINANCE` | Accounts | Payout requests, insurance premium report, policies (read) | Documents; marking payouts paid (owner only until the two-person rule, Phase 3) |
| `MARKETPLACE_MODERATOR` | Seller desk | Seller shops | Rides, partners, payments, documents |
| `AUDITOR` | The CA, time-limited | Finance reports, invoices and payments, read-only | Change anything; access ends on its date by itself |

### How it works

- **Sign-in:** work email + password + a 6-digit authenticator code, in one
  step, answered with one sentence for every wrong answer. **No SMS** for
  staff. Passwords: 12+ characters, not on the bundled common-password
  list (also with digits or symbols tacked on), Argon2id-hashed, no forced
  rotation. Ten single-use recovery codes, stored as HMACs, stand in for a
  lost phone. Five wrong answers lock the account for 15 minutes; per-email
  and per-address rate limits give unknown emails the same refusals.
- **Invitations, never sign-up:** an owner or manager invites by email; the
  link works once, for 24 hours; the invitee chooses a password, then scans
  a QR code and types back a code, then saves recovery codes. Only then
  does the account exist. No default passwords anywhere. Without SMTP the
  link is shown once to the inviter to pass on privately.
- **First owner:** with no active OWNER, each start invites
  `SHEOUT_OWNER_BOOTSTRAP_EMAIL` (emailed, or written to the log at INFO when
  email is not set up). Once an owner exists the variable does nothing.
- **Sessions:** an HttpOnly, Secure, SameSite=Strict cookie sent only to
  `/api/v1/admin`; the page cannot read it and nothing is kept in
  localStorage. Every change needs the session's CSRF token and must not
  come from another site. 30 minutes idle (the console's own polling does
  not count) and 8 hours in all - owners 15 minutes idle, the safety desk
  60 minutes and 12 hours; the console warns two minutes before.
  Each session is an `account_sessions` row too, so it is listed, ended and
  checked like any other - an ended session is refused on its next request.
- **Authorisation:** every `/api/v1/admin/**` endpoint declares
  `@RequiresPermission`, `@RequiresAnyPermission`, `@StaffSignedIn` or
  `@StaffPublic`; one with none stops the server starting
  (`AdminEndpointGuard`). `StaffPermissionInterceptor` checks it before the
  endpoint runs; `StaffContext.require(...)` checks what depends on the data
  (which role is being granted).
- **Offboarding:** disabling someone ends every session and SOS push
  registration at once; only an owner can re-enable. Changing a role ends
  her sessions too. An owner can reset someone's password and
  authenticator: the old ones stop working and she gets a link to choose new
  ones for the same account.
- **The old way in is closed:** `ADMIN_PHONE_LOGIN_ENABLED=false`
  (production) refuses phone-code sign-in as ADMIN and ignores any ADMIN
  bearer token still held. `AdminBootstrap` (promote a phone number to
  ADMIN) is gone.

### Phase 2: protection

- **Masked by default.** Every console response passes through one Jackson
  module (`staff.internal.ConsoleMasking`): any field named like a phone
  holding 10-13 digits shows as `98•••••210`; fields marked
  `@Pii(ADDRESS)` (trip pickup and drop, saved home and work) show as their
  area ("Banjara Hills, Hyderabad"), home/work coordinates to about a
  kilometre, PAN as `•••••1234F`, bank account and UPI as the last four -
  except for roles that send payouts (`payouts.prepare`). Owners see the
  masked form too. Nothing changes in the apps: masking applies only when a
  member of staff is on the request.
- **Reveal with a reason.** `POST /api/v1/admin/reveal` shows one value:
  it needs `pii.phone.reveal` or `pii.address.reveal`, a fresh
  authenticator code, a typed reason, and the record within reach. The
  reason is kept in the audit log.
- **Step-up.** Endpoints marked `@RequiresStepUp` (reveals, opening an ID
  or police document, marking payouts paid, every staff change, insurance
  policy changes) and every `@Export` need the authenticator code entered
  on this session in the last 5 minutes (`STAFF_STEP_UP_MINUTES`); the
  console asks in a layer of its own and repeats the request.
- **Scoping.** A verification agent opens only partners she has taken from
  the queue (`/api/v1/admin/work/verification/{id}/take`), and two agents
  can never hold the same one; `verification.all` (managers, owners) opens
  any. A support agent sees unassigned tickets and her own, opens only her
  own, and can only take one herself; `support.all` sees and assigns all.
  The safety desk reaches a trip, its live position
  (`/api/v1/admin/alerts/trips/{id}/live`) and the phone numbers of the
  people in it only while an SOS or trip alert on it is open or closed less
  than 30 minutes ago (`STAFF_ALERT_ACCESS_MINUTES`).
- **Audit log.** `staff_audit_events`: every console change, every marked
  read (`@AuditedRead`: documents, trip detail, the live board, the audit
  log itself), every export, every refusal (`permission.denied`), every
  sign-in, failure and lock, every reveal with its reason, every staff
  change with before and after. The verification module's operator entries
  are copied in, so document views are here too. A database trigger refuses
  UPDATE, DELETE and TRUNCATE; each row stores the hash of the one before,
  and a nightly job (`STAFF_AUDIT_CHECK_CRON`, 03:30 IST) recomputes the
  chain. The Audit page (owners, `audit.view`) shows alerts first, filters
  by person, action and dates, and exports CSV (step-up, audited, alerted).
- **Alerts to owners** - push to their browsers, email when SMTP is set, and
  a row the Audit page shows: an account locked by failed sign-ins, any
  invitation, role change, disable, re-enable or reset, any export, more
  than 20 reveals in 10 minutes by one person, a sign-in from a browser not
  seen before (she is told too), and a broken chain.

### Setting up the first owner on Render

1. Set `STAFF_SECRETS_KEY` (`openssl rand -base64 32`) and keep a copy
   somewhere safe. Set `SHEOUT_OWNER_BOOTSTRAP_EMAIL` to the founder's work
   email and `STAFF_CONSOLE_URL` to the console's address. Optionally keep
   `ADMIN_BOOTSTRAP_PHONE` set to the old operator number so her history
   stays hers.
2. Deploy. Without SMTP, open the service log and find the line
   `STAFF OWNER INVITATION for ...`; with SMTP it is emailed.
3. Open the link within 24 hours, choose a password, scan the QR code, save
   the recovery codes. Then invite a second owner from Staff.

### Configuration

| Key | Default | What |
|---|---|---|
| `STAFF_SECRETS_KEY` | none (required) | Encrypts authenticator secrets, keys recovery-code hashes |
| `SHEOUT_OWNER_BOOTSTRAP_EMAIL` | blank | First-owner invitation, only while no active owner exists |
| `STAFF_CONSOLE_URL` | `http://localhost:8080/admin/` | Base of invitation links |
| `STAFF_INVITE_HOURS` | 24 | How long an invitation link works |
| `STAFF_SESSION_IDLE_MINUTES` / `STAFF_SESSION_ABSOLUTE_HOURS` | 30 / 8 | Idle sign-out and longest sign-in, every role without its own |
| `STAFF_SESSION_IDLE_MINUTES_OWNER` / `STAFF_SESSION_ABSOLUTE_HOURS_OWNER` | 15 / 8 | Owners |
| `STAFF_SESSION_IDLE_MINUTES_SAFETY_RESPONDER` / `STAFF_SESSION_ABSOLUTE_HOURS_SAFETY_RESPONDER` | 60 / 12 | The night-shift safety desk |
| `STAFF_AUDITOR_DEFAULT_DAYS` | 30 | An auditor invited without an end date |
| `REFUND_LIMIT_SUPPORT` / `REFUND_LIMIT_MANAGER` / `REFUND_LIMIT_FINANCE` | 200 / 1000 / 200 (₹) | Refunds without an owner's approval (`staff.RefundLimits`); owners unlimited |
| `STAFF_IP_ALLOWLIST_<ROLE>` | blank (off) | Addresses or CIDR ranges a role may use the console from, e.g. `_OWNER`, `_FINANCE`; checked at sign-in and on every request |
| `STAFF_LOGIN_MAX_FAILURES` / `STAFF_LOGIN_LOCK_MINUTES` | 5 / 15 | Lockout |
| `STAFF_LOGIN_PER_ADDRESS` | 20 | Sign-in attempts per network address per 15 minutes |
| `STAFF_COOKIE_SECURE` | true | Off only for plain-HTTP testing on a host that is not localhost |
| `ADMIN_PHONE_LOGIN_ENABLED` | false (true in the local profile) | The old phone-code console sign-in |
| `ADMIN_BOOTSTRAP_PHONE` | blank | Old operator account the first owner adopts; grants nothing |
| `STAFF_STEP_UP_MINUTES` | 5 | How long a re-entered code covers sensitive actions |
| `STAFF_ALERT_ACCESS_MINUTES` | 30 | How long after an alert closes the safety desk keeps reach |
| `STAFF_AUDIT_CHECK_CRON` | `0 30 3 * * *` | When the audit chain is checked (India time) |

### Flagged assumptions

- **Each staff member is backed by an `accounts` row** (role ADMIN, no
  phone, no email). Every module already records "who decided" by account
  id and pushes SOS alerts to ADMIN devices; this kept all of that working
  without touching those modules. There is nothing to sign in to an app with.
- **Permissions beyond the brief's list** were needed for surfaces the
  console already has: `support.work`, `users.view`, `campaigns.manage`
  (owner only - it spends money), `service.hours.manage` (owner and manager
  - pausing bookings is an emergency control), `content.manage`,
  `announcements.send`.
- **Until the two-person rule (Phase 3), only an OWNER can mark a payout
  paid** (it needs both `payouts.prepare` and `payouts.approve`).
- **Phase 1 is not yet scoped by data**: a safety responder does not get the
  live map of all trips (it needs `trips.view` too), and support agents do
  not see trips outside tickets, by keeping those endpoints from them, not
  by filtering. Masked phone numbers and addresses come in Phase 2, so roles
  that may open a list see the numbers in it today.
- **Without SMTP an invitation link is shown to whoever sent it.** That
  person could accept it themselves. It is a link to an account of a role
  they could already grant; once audit exists (Phase 2) it is visible.
- **The console still loads Firebase's messaging script** from gstatic, for
  SOS push to operators' browsers. Phase 4 adds Server-Sent Events; until
  then removing it would remove SOS alerts to a closed tab.
- **Finance's refund limit was not given**, so it is the support limit
  (₹200) until decided. Nothing issues refunds from the console yet: the
  refund endpoint arrives with Phase 3's approvals and checks
  `RefundLimits` before paying.
- **The audit table is owned by the app's own database role on Render**, so
  a role without UPDATE/DELETE on it is not available there: the trigger
  stops the app and casual edits, and the hash chain is what catches
  someone with the database password who drops the trigger first.
- **Masking is by field name and marking.** A phone number typed into free
  text (a ticket description, a note) is not masked.
- **New-browser detection uses the User-Agent**, which can be copied: a
  tripwire for the ordinary case, not a defence on its own.
- **Without SMTP, alerts reach owners by push and the Audit page only.**
- **Polling requests are marked by the console** (`X-Staff-Background`).
  Idle time is a safeguard against a forgotten tab, not against an attacker
  who has the session already.

### Not built yet (later phases)

Phase 3: four-eyes approvals (exports, payouts, refunds above the limits,
manager/owner changes, configuration), the refund endpoint itself, the
payout two-person rule, config history. Phase 4: role
dashboards, live ops board with SSE, queue assignment and SLAs, passkeys for
owners, a separate admin hostname. (The IP allowlist is built and off.)

## Partner documents and expiry

A partner's driving licence, vehicle RC, vehicle insurance, PUC and (for
autos and cabs) fitness certificate - `driververification`'s
`PartnerDocumentService`, table `partner_documents` (V55).

### Flow

1. She sends each document from her checklist, one at a time:
   **`POST /api/v1/driver-verification/partner-documents/{type}`**
   (multipart: `file`, `documentNumber`, `issuedOn`, `validUntil`, and for
   insurance `insuranceUseType` = `COMMERCIAL`/`PRIVATE`/`UNKNOWN`). What she
   reads off the document is required, so the operator checks her answer
   against the image rather than transcribing it. A new upload supersedes
   the old row; it never overwrites it.
2. An operator decides each one:
   **`POST /api/v1/admin/verification/documents/{documentId}/review`**
   `{ decision, reason, documentNumber, issuedOn, validUntil, insuranceUseType }`
   - the fields are corrections. Approving needs the file, the number, the
   valid-until date (not already past) and, for insurance, a commercial-use
   policy. Rejecting needs a reason, which she is sent.
   An operator can also put a document on file for her
   (`POST /api/v1/admin/verification/{accountId}/documents/{type}`), and it
   goes through the same review.
3. Opening a file goes through
   **`GET /api/v1/admin/verification/documents/{documentId}/link`**, which
   writes "who opened which document, when" to `verification_audit_events`
   before handing out the signed, expiring link.
4. **The gate.** `VerificationApi.partnerReadiness(accountId, vehicleType)`
   answers "may she take trips now": ID check, every required document
   approved and in date, police check. `users`' online gate asks it live, and
   so does dispatch through `DriverProfileApi.isCurrentlyVerified` before
   every offer. Going online with something missing answers `409 NOT_READY`
   with the first reason ("Your vehicle insurance expired on 12 Nov 2026.
   Upload the new one to go online."). Her app reads the whole picture from
   **`GET /api/v1/users/driver/me/readiness`**.
5. **Expiry** (`PartnerDocumentExpirySweeper`, hourly, behind `ClusterLock`):
   a document past its valid-until date becomes `EXPIRED` and
   `VerificationLapsed` is published. `users` takes her offline - a trip
   she is on is not touched by that, so she finishes it - and dispatch
   offers her nothing more. She is reminded 30, 7 and 1 days before, once
   each, in her language.
6. **Renewals.** A renewal sent before the old document runs out does not
   take her off the road while it waits; if it is turned down, the earlier
   approved one becomes current again.

### Configuration

All in `.env.example`: `PARTNER_SERVICES_<VEHICLE>` (which services a
vehicle is sent on), `PARTNER_DOCS_<SERVICE>` (what each service needs),
`COMMERCIAL_INSURANCE_SERVICES`, `DOCUMENT_REMINDER_DAYS`,
`PARTNER_DOCUMENT_SWEEP_INTERVAL_MS` and
`PARTNER_DOCUMENTS_REQUIRED_TO_GO_ONLINE`. An unknown document name stops
the server starting.

### Flagged assumptions

- **Deploying this takes every existing partner offline** until her
  licence, RC, insurance and PUC are uploaded and approved.
  `PARTNER_DOCUMENTS_REQUIRED_TO_GO_ONLINE=false` is the rollout switch for
  those days: she is shown what is missing but can still work. It is not a
  bypass - set it back to `true` once the console's "Documents to review"
  queue is empty.
- **Existing RC photos were moved, not re-collected.** V55 copies every
  `verification_records.rc_document_key` into `partner_documents` as an
  `UNDER_REVIEW` `VEHICLE_RC`, sharing the stored file: the old review read
  the RC beside the ID, but never recorded its number or how long it is
  valid, so an operator reads those off and approves it again. The old
  column is no longer written or read for review, and is kept only so the
  release that is still serving while this one starts does not break.
  It is dropped by `V60__drop_rc_document_key.sql` on the
  `next/drop-rc-document-key` branch, which ships in the release *after*
  this one is live, never together with it. V60 first copies any RC an
  older backend wrote to the column after V55 ran. If `main` gains other
  migrations in the meantime, renumber it to come after them when rebasing.
- **The ID submission no longer requires the RC.** The RC is its own
  checklist step now. An RC that an older app still sends with the ID is
  filed as her `VEHICLE_RC` for review.
- **Commercial insurance at review time is checked strictly.** The
  operator's approval does not know her vehicle, so it requires a
  commercial policy whenever any vehicle SheOut takes carries passengers -
  which is all of them today. The gate itself checks per vehicle.
- **The services-per-vehicle map repeats dispatch's matching rule**
  (parcels go to bikes) rather than importing it, to avoid a module cycle.
  Change both together.
- **A vehicle change approved in Profile Changes does not touch her
  documents.** The new vehicle's insurance and PUC have to be uploaded as
  renewals; nothing yet asks for them automatically.
- **Every operator view of any partner document is logged**, not only
  police certificates - the cost is a row per view.

### In the console

- **Four queues** under Operations: *Documents to review* (oldest first),
  *Expiring in 30 days*, *Expired / blocked* and *Police re-verification
  due*, sortable like the others, read from
  `GET /api/v1/admin/verification/document-queue?queue=TO_REVIEW|EXPIRING|EXPIRED|POLICE_DUE`.
- **A partner's detail has three tabs**: Overview, *Documents & police
  check* (each required document as a card with its number, dates, an
  amber "Expires in N days" or red "Expired", the file viewer, and
  Approve/Reject with the fields to correct; the police check form) and
  *Audit trail*. One fetch, `GET /api/v1/admin/partners/{accountId}/verification`,
  which carries no file URLs.
- **The ID review no longer shows the RC or a police Approve button.** The RC
  is reviewed as a document of its own; the police check is recorded on the
  Documents tab with its evidence. Approve buttons stay disabled for the
  same reasons the server refuses (and say which), so the rule is learned
  before the refusal.

## Police verification with evidence

The 2025 Motor Vehicle Aggregator Guidelines require police verification of
drivers. It used to be an Approve button that recorded nothing about what
it rested on. It is now a recorded fact with evidence, redone on a period -
`PoliceVerificationService`, table `police_verifications` (V56).

**Why a new table** rather than columns on `verification_records`: a police
check is redone every `POLICE_REVERIFY_MONTHS`, and each decision has its
own evidence. Columns would be overwritten by the next check, losing what
the previous one rested on - and "was she police-verified, on what, on the
night of trip X" is exactly what a complaint or an audit asks.
`verification_records` keeps only the live state (status, due date,
consent) the gate reads.

### Flow

1. **Consent first.** Before she uploads anything, a partner agrees to the
   versioned consent (`PARTNER_VERIFICATION_CONSENT` in
   `design-system/src/legal/content.ts`, every paragraph marked
   `[to be reviewed by lawyer]`):
   **`GET`/`POST /api/v1/driver-verification/consent`** `{ version }`. The
   server accepts only `VERIFICATION_CONSENT_VERSION`; a newer wording asks
   everyone again. Uploads, operator uploads on her behalf and a police
   approval all answer `CONSENT_REQUIRED` without it. Riders are not asked:
   their ID check rests on the privacy policy they accepted at signup.
2. **The certificate**, either way: she applies on Telangana Police's portal
   (https://pvc.tspolice.gov.in/) and uploads it as `POLICE_CERTIFICATE`, or
   an operator uploads it after SheOut obtains it
   (`POST /api/v1/admin/verification/{accountId}/documents/POLICE_CERTIFICATE`).
3. **The decision**: `police-review` with
   `{ decision, method, certificateNumber, issuingAuthority, issuedOn,
   reverifyDueOn, evidenceDocumentId, extraEvidenceDocumentId, reason }`.
   VERIFIED needs the method (`TS_POLICE_PVC`, `OTHER_STATE_POLICE`,
   `THIRD_PARTY_BGV`), the certificate number, the issue date and an
   evidence document of hers that is on file and not turned down - otherwise
   `POLICE_EVIDENCE_MISSING`/`POLICE_EVIDENCE_INVALID`. The re-verify date
   defaults to issue date + `POLICE_REVERIFY_MONTHS`. Approving also approves
   the certificate document. REJECTED needs a reason. The "ID check first"
   guard is unchanged.
4. **Re-verification**, in the same hourly sweep as document expiry:
   reminders 30 and 7 days before (`POLICE_REVERIFY_REMINDER_DAYS`), then on
   the due date the check goes back to `PENDING` and `VerificationLapsed` is
   published - `users` clears its cached `verified` flag and takes her
   offline after any trip she is on. The gate does not wait for the sweep:
   a check whose date has arrived blocks her at once.

### Swap point: `VerificationProvider`

`driververification.internal.provider.VerificationProvider`
(`submitBackgroundCheck`, `fetchResult`, `parseWebhook`), selected by
`VERIFICATION_PROVIDER`. The only implementation is
`ManualVerificationProvider`, which does nothing: operators record results.
**No vendor is integrated and no vendor's API is guessed at** - a vendor's
request and webhook formats belong in its own implementation, written
against its real documentation. A result arrives as a
`BACKGROUND_CHECK_REPORT` for an operator to read; it decides nothing.

`POST /api/v1/verification/provider/webhook` is a stub: 404 unless a
provider is configured and `VERIFICATION_PROVIDER_WEBHOOK_SECRET` is set,
then an HMAC-SHA256 of the raw body (hex, in the header named by
`VERIFICATION_PROVIDER_SIGNATURE_HEADER`) checked in constant time, and the
body handed to the provider.

### Flagged assumptions

- **A third-party background check alone is NOT accepted as police
  verification** (`POLICE_ACCEPT_THIRD_PARTY_BGV_ALONE=false`). Whether a
  private background check by itself satisfies MVAG's "police
  verification" in Telangana is a legal question nobody has answered. Until
  a lawyer says it does, a report can be attached beside a police
  certificate but cannot make the check VERIFIED.
- **Twelve months between police checks** is an assumption
  (`POLICE_REVERIFY_MONTHS`).
- **Every police check recorded before V56 had no evidence**, so V56 makes
  each one due the day it is deployed. The first sweep puts them back to
  `PENDING` and tells each partner to send a certificate; operators then
  record them with evidence. Deploying this takes every existing partner
  offline until that is done (as Part A's documents also do).
- **The consent wording is a draft.** It is in the design system with
  `[to be reviewed by lawyer]` on every paragraph and version
  `2026-10-03-draft`; when approved, change the text and bump the version
  in both places.
- **A rejected police check is not pushed to her** - she sees it, with the
  reason, on her checklist. The ID check's rejection is pushed; this one
  could be, with its own copy, once the wording is agreed.
- **Aadhaar:** this pass collects nothing new about Aadhaar. The lawyer
  should confirm before launch whether the ID photo SheOut already takes
  should be limited to a masked Aadhaar (the app already invites her to
  cover the number) or replaced by DigiLocker.

## Insurance

`com.sheout.insurance`, tables `insurance_policies`, `trip_coverages`,
`trip_coverage_claims`, `partner_insurance_enrolments` (V57).

**The decision:** every trip is covered under a master group policy, built
in rather than sold as a toggle. The premium is a **platform cost recorded
per trip and paid out of SheOut's commission**. It is not added to the
rider's fare, not taken from the partner's share, and not part of the 80%
check. The rider sees an "Insured trip" chip and the policy and claim
details, never a charge line. (Uber India sells rider cover as a ₹3 opt-in;
Rapido's Acko cover is opt-in too. MVAG 2025 expects at least ₹5 lakh of
passenger cover on every trip.)

### Flow

1. **Policies are entered in the console** (Insurance page) from the
   insurer's schedule: kind (`PASSENGER_TRIP`, `GOODS_IN_TRANSIT`,
   `PARTNER_HEALTH`, `PARTNER_TERM_LIFE`, `PARTNER_ACCIDENT`), insurer,
   master policy number, sum insured, premium and unit (`PER_TRIP` for trip
   and goods cover, `PER_MEMBER_PER_YEAR` for partner covers), effective
   dates, claims phone and links, and what is covered and how to claim, in
   the schedule's words. Saved switched off; one active policy per kind; an
   active policy is not edited (trips point at it) - switch it off and add
   the new terms. **No policy is hardcoded anywhere.**
2. **Trip cover** is opened on `BookingStarted` (rides: `PASSENGER_TRIP`,
   parcels: `GOODS_IN_TRANSIT`) when a policy of that kind is in force, with
   the premium on the row, and closed on `BookingCompleted` (or a
   cancellation, if one ever happens after a start). `BookingStarted` now
   carries the category and route, so insurance never calls booking - booking
   asks insurance whether rides are covered, and the two would otherwise wait
   on each other at startup.
3. **No active passenger policy means no chip.** The apps ask
   `GET /api/v1/insurance/trips/{bookingId}` (404 without cover) and
   `GET /api/v1/insurance/passenger-cover`; with nothing in force they say
   nothing about insurance, and the console shows a red "Trips are NOT
   insured: no active passenger policy" banner. With
   **`INSURANCE_REQUIRED_FOR_RIDES=true`** ride requests are refused with
   `RIDE_INSURANCE_NOT_ACTIVE` until one is switched on. It is **false
   everywhere while SheOut is pre-launch** and has no insurer (so internal
   test rides are not refused); turn it on once a real passenger policy is
   active - see `docs/PENDING_DECISIONS.md`. Deliveries are never refused for this.
4. **Reporting** goes through `InsurerReporter`. The default
   `CsvBordereauReporter` produces the day's file - booking id, insurer,
   policy number, start and end (IST), category, pickup and drop *area*,
   premium - which an operator downloads from the console
   (`GET /api/v1/admin/insurance/bordereau?date=`) and sends. Downloading
   marks those trips `REPORTED`. `ApiInsurerReporter`
   (`INSURANCE_REPORTER=api`) is a stub that refuses, marking trips `FAILED`
   rather than pretending; a `FAILED` trip is no longer shown as insured.
5. **Premiums** are summed per policy per month
   (`GET /api/v1/admin/insurance/premium-report?month=`). They never change
   `FareQuote.amount`, what the rider is charged, `driverPayout` or the
   commission - `PremiumIsAPlatformCostTest` fails the build if the insurance
   module ever imports payments, payouts or booking's fare code, or they it.
6. **Partner group cover**: on `AccountVerified` a partner is put forward for
   each partner cover in force as `PENDING_ENROLMENT`; an operator marks her
   `ENROLLED` with the member id once the insurer confirms. Her app shows a
   cover only when `ENROLLED` (`GET /api/v1/insurance/me/covers`). She is
   `EXITED` when her account is deleted or blocked (new `auth` event
   `AccountBlocked`). Monthly joiners and leavers:
   `GET /api/v1/admin/insurance/enrolments/movements?month=` (name, date of
   birth, phone - what the insurer needs, and what her consent says it gets).
7. **Claims**: "Report an accident / make a claim" on a trip that started
   (`POST /api/v1/insurance/trips/{bookingId}/claim`) raises a HIGH-priority
   support ticket in the new `ACCIDENT_OR_INSURANCE_CLAIM` category, linked to
   the trip, recorded beside its cover, and answers with the insurer's claim
   steps and phone. SheOut helps; the insurer decides. A trip with no cover
   can still report an accident - the answer then names no insurer.

### Configuration

`INSURANCE_REQUIRED_FOR_RIDES` (false until a real passenger policy exists),
`INSURANCE_REPORTER` (`csv`), `INSURANCE_BADGE_REQUIRES_REPORTED` (false).

### Flagged assumptions

- **When the chip appears.** The ground rule says the rider app must not say
  "insured" unless a policy is active *and the trip was reported*; Part E
  puts the chip on the live trip screen. A daily bordereau is not sent until
  the day ends, so read literally the chip could never appear during a trip.
  It is shown from a coverage row against a policy that was in force when
  the trip started, and **hidden if reporting failed**.
  `INSURANCE_BADGE_REQUIRES_REPORTED=true` makes it appear only once the
  trip is in a downloaded bordereau. **Ask the broker whether cover attaches
  at the trip or at the declaration**, and set this to match.
- **`REPORTED` means "in a bordereau an operator downloaded"** for the CSV
  reporter. Whether it was actually sent is outside SheOut's records.
- **Switching `INSURANCE_REQUIRED_FOR_RIDES` on refuses every ride** until a
  passenger policy is entered and switched on in the console. Enter the
  policy first, then switch it on.
- **Pickup and drop areas** are the last two comma-separated parts of the
  address with anything containing a digit dropped ("Jubilee Hills,
  Hyderabad") - enough to place a claim, never a house number. A heuristic.
- **Partners verified before a partner cover existed are not enrolled
  automatically** - only those verified after it is switched on. An
  unblocked partner is not re-enrolled automatically either.
- **Per-member premiums are not accrued monthly** here; the premium report
  covers per-trip premiums. Reconcile group-cover premiums against the
  insurer's invoice.
- **booking and insurance now refer to each other** at the package level
  (booking's gate reads `InsuranceApi`; insurance listens to booking's
  events), the same shape as booking and campaigns. There is no bean cycle:
  claims live in their own bean for that reason.

## Commission ceiling and GST

**The partner keeps at least 80%.** MVAG 2025 says partners using their own
vehicle receive at least 80% of the fare. `PlatformCommission` used to
accept anything from 0 to 100; it now refuses to start if
`PLATFORM_COMMISSION_PERCENT` is above `PLATFORM_COMMISSION_MAX_PERCENT`
(default `20.00`), naming the rule.

**One price up front; the tax inside it on the receipt.** The booking screen
shows one all-inclusive price, as before. With GST on, each captured payment
is split into its taxable value and tax at the rate for its kind of supply
(`BIKE`, `AUTO`, `CAB`, `PARCEL`, `SELLER_LISTING_FEE`; `PLATFORM_FEE` is
configured for when SheOut's own fee is invoiced separately), stored on the
payment, and a tax invoice is issued: a gapless per-financial-year number
(`SO/2026-27/000001`, taken under a row lock inside the capture so a failed
capture gives its number back), SheOut's GSTIN, place of supply (Telangana),
the SAC code, her name, the taxable value, CGST and SGST. The rider's receipt
then shows the tax included and "Download tax invoice"
(`GET /api/v1/payments/bookings/{id}/tax-invoice[.pdf]`, hers only). The PDF
is drawn by `InvoiceRenderer`; the default `SimplePdfInvoiceRenderer` writes
a plain one-page PDF with no library.

**GST is off by default (`GST_ENABLED=false`) and every rate is empty.** With
it on and the GSTIN, legal name, any rate or any SAC code missing, the server
refuses to start and lists what is missing. `FARES_TAX_INCLUSIVE=false` is
refused rather than built, because tax added on top would change what riders
pay.

> **Before `GST_ENABLED` is turned on, SheOut's CA must sign off:** the rate
> for each kind of supply, the SAC codes, **who the supplier is** - whether
> SheOut is liable as the e-commerce operator under Section 9(5) for
> app-booked passenger transport (and the rate that then applies), or only
> for its platform fee - and the invoice format and numbering. **The answer
> changes if SheOut moves to a subscription model**, where it charges
> partners a fee instead of a commission and may no longer be the supplier
> of the ride at all.

### Flagged assumptions

- **Tax is worked out on what the rider paid** (`amount`), after any
  promotion; a trip a promotion paid in full gets no invoice. Whether a
  SheOut-funded promotion reduces the taxable value is for the CA.
- **The tax comes out of SheOut's side.** Prices are inclusive and the
  partner's share is still computed on the whole fare exactly as before, so
  with GST on the tax is borne from SheOut's commission. Who bears it is a
  business decision for the founder and CA.
- **Intra-state only**: every trip starts in Telangana, so the tax is split
  into CGST and SGST and IGST is always zero.
- **Lunch Box is taxed as a parcel** until the CA says otherwise; the apps do
  not offer it.
- **Not built**: e-invoicing (IRN/QR from the GST portal), credit notes for
  refunds, and invoicing the partner for SheOut's commission. A renderer for
  an e-invoicing provider is a new `InvoiceRenderer`.

## What riders and partners see

**Rider app.** The booking screen shows one all-inclusive price, unchanged -
no insurance line, no tax line. Once a trip starts, the trip screen shows an
"Insured trip · ₹5,00,000 cover" chip **only** if the server returns a cover
for that trip; tapping it shows the insurer, policy number, what is covered
and how to claim, and "Report an accident or make a claim". The finished
trip (opened from My Bookings, which is her receipt) adds *Fare details* -
base fare, distance, time, night or busy-time multiplier, the minimum-fare
note, the promotion and what she paid - from `GET /api/v1/bookings/{id}/fare-details`.
The Safety Center says "Every SheOut trip is insured" only while a passenger
policy is in force.

**Partner app.** *Verification* is a checklist in the order she does it:
consent, ID and live selfie, driving licence, vehicle RC, vehicle insurance,
PUC (and fitness for an auto or cab), police certificate - each with its
status, the reason if it was turned down, when it runs out, and an upload
that asks for the number and dates printed on it (and, for insurance,
commercial or private use). The police step links to Telangana Police's
portal with three steps. *Home* shows a banner with the first thing stopping
her and a button to the checklist; a `NOT_READY` refusal opens the checklist.
*Earnings* shows each trip's fare, SheOut's commission and her share from
the payment record, and "SheOut pays your trip insurance; nothing is
deducted from you" only while a passenger policy is in force. *Profile*
shows "Your cover" only for group covers she is `ENROLLED` in.

### Flagged assumptions

- **The fare breakdown is kept from V58 on.** Trips booked before it, and
  trips re-priced by a destination change, show the total and say why there
  is no breakdown.
- **The rider's receipt is the finished trip screen**, which My Bookings
  opens; there is no separate receipt page.
- **The chip and the checklist copy are translated by machine** into Hindi
  and Telugu, like the rest of the apps' new copy, and need the same
  native-speaker review. The consent itself is shown in English, as the other
  legal documents are.

### How the documents, police, insurance and GST work was checked

- **Tests**: a unit test for each rule (evidence required for a police
  VERIFIED, a background check alone refused by default, expiry blocking,
  the commission ceiling, the insured chip only with an active policy, the
  premium never touching fare or payout, GST off by default and refusing to
  start half-configured, gapless invoice numbers) and integration tests on
  the local Postgres and Redis. `MigrationPathTest` runs every migration from
  an empty schema, and from production's shape today (V54) with an RC photo
  and an unevidenced police check in it, to the latest.
- **A run of the apps** (backend, both apps and the console on this
  machine's Postgres and Redis - not docker compose, which this machine does
  not have): a new partner consents and sends her documents in the partner
  app; an operator approves them and records the police check with evidence
  in the console; she goes online; a ride is refused with no passenger
  policy and taken with one; the rider sees "Insured trip" on the live trip;
  the trip completes and is paid; the receipt shows the fare details and no
  insurance line; the coverage row is in the day's bordereau; an accident
  report becomes a HIGH ticket; and when her PUC expires she is taken offline
  and told why. Two steps were set in the database rather than driven
  through a camera: her ID and live selfie, and the start-of-shift selfie
  (the backend ran with `SHIFT_CHECK_ENABLED=false`). The script is outside
  the repository (`D:\sheout-work\qa\full_e2e.mjs` on the test machine).

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

### Reset the local database

The local database collects test accounts (load-test riders and partners,
QA partners, test trips). To start again from nothing:

```bash
# Postgres: an empty database, owned by the app's role.
psql -h localhost -U postgres -c "drop database if exists sheout with (force)" \
                              -c "create database sheout owner sheout"
# Redis: OTP codes, rate limits, dispatch state.
redis-cli -p 6379 flushall          # or memurai-cli
# Start the backend: Flyway builds the schema from V1.
cd backend && mvn spring-boot:run
```

Then start the backend once with `SHEOUT_OWNER_BOOTSTRAP_EMAIL=<you@example.com>`:
with no owner yet, it writes an OWNER invitation link to the log (local has no
mail server). Open it, choose a password, scan the QR code with an
authenticator app, and you are the first owner; invite everyone else from the
console's Staff page. Local scripts that still sign in to the console API
with a phone number work only because `application-local.yml` turns
`ADMIN_PHONE_LOGIN_ENABLED` on. There are no
insurance policies after a reset - add one in the console's Insurance page
only for local testing, and never on a shared environment. The load test
(`loadtest/sheout-load.mjs`) seeds its own partners' consent, police check
and documents each run (`--consent-version` must match the server's
`VERIFICATION_CONSENT_VERSION`).

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

`RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` / `RAZORPAY_WEBHOOK_SECRET` are
blank by default. Without them trips still complete and cash still works,
but online payment reports itself unavailable. With test-mode keys, point a
Razorpay webhook at `/api/v1/payments/webhooks/razorpay` for the
`payment.captured` and `payment.failed` events, using the same webhook secret:
Checkout's own success callback is verified too, but the webhook is what
catches a rider who closes the page before it returns.

`FIREBASE_PROJECT_ID` / `FIREBASE_CREDENTIALS_JSON` are placeholders in both
profiles.

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

### VITE_MAP_TILE_URL / VITE_MAP_TILE_ATTRIBUTION

Optional, and unset by default, in which case `LiveMap` uses standard
OpenStreetMap tiles - the same ones it has always used.

The intended look is CARTO's **Voyager** style: pale, with road hierarchy
and place names still legible, so this app's purple/teal/red markers and
the route line read as the foreground instead of competing with a
saturated basemap. CARTO **now requires an API key** for its basemaps.
Keyless tiles are still served, but with `API KEY REQUIRED /
carto.com/basemaps/apikey` printed diagonally across every one of them, at
every zoom - which is not something to ship on a map a rider is meant to
trust.

Their free tier is enough for this. Once you have a key, set on **both**
Vercel projects (no code change, no release of `@sheout/design-system`):

```
VITE_MAP_TILE_URL=https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png?api_key=YOUR_KEY
VITE_MAP_TILE_ATTRIBUTION=&copy; OpenStreetMap contributors &copy; CARTO
```

The braces are Leaflet's own placeholders and must be left exactly as they
are. Set the attribution whenever you set the URL: a tile provider's
attribution line is usually a term of use, not decoration.

Any other raster provider works the same way - this is a URL, not a CARTO
integration.

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

## Monitoring, alerting and backups

### Crash reporting (Sentry)

Both apps and the backend report unhandled exceptions to Sentry, and are
silent about it until a DSN is set:

| Where | Variable | Set it in |
| --- | --- | --- |
| Backend | `SENTRY_DSN` | Render dashboard |
| Rider app | `VITE_SENTRY_DSN` | Vercel project env (build time) |
| Partner app | `VITE_SENTRY_DSN` | Vercel project env (build time) |

Unset means no client is created and nothing leaves the process, which is
what local runs get. The frontends read theirs at **build** time, so a
newly set DSN needs a redeploy, not a restart.

What is deliberately not sent: performance tracing and session replay are
both off. Replay records what somebody types and sees, which on these apps
is her home address, her live location and her chat with a stranger.
Phone numbers are masked out of messages, exception text and breadcrumbs
before an event is sent (`platform.SentryConfig`, `lib/errorReporting.ts`),
and `send-default-pii` is off, so no IP addresses travel either.

### Uptime monitoring - set this up, it is not code

Nothing in this repository notices that the service is down; Sentry only
reports crashes the service is alive enough to report. Point a free
external monitor at all three URLs:

| Check | URL | Expect |
| --- | --- | --- |
| Backend | `https://sheout-backend.onrender.com/actuator/health` | 200, body contains `"status":"UP"` |
| Rider app | `https://sheout-customer-app.vercel.app/` | 200 |
| Partner app | `https://sheout-driver-app.vercel.app/` | 200 |

UptimeRobot's free plan covers this (50 monitors, 5-minute interval,
email alerts; SMS costs extra). Any equivalent - Better Stack, Healthchecks.io,
Pingdom's free tier - does the same job. Two notes specific to this
deployment: a 5-minute ping also keeps Render's free instance from
spinning down, so cold starts mostly stop happening; and the alert should
go somewhere that wakes somebody, because the thing being monitored is how
a woman gets home at night.

### Database backups - the free plan has none

The live database is **Render's own free Postgres** (see `render.yaml`) -
not Neon, not Supabase. Render's documented free-plan terms, which are the
operative fact here:

- **No backups at all.** "Render does not provide recovery capabilities
  for databases on the Free compute plan", and no logical backups are
  created either. There is nothing to restore from.
- **It expires 30 days after creation.** After that there are 14 days to
  upgrade before Render permanently deletes the database and everything in
  it.
- 1 GB of storage, one free database per workspace.

That now includes every uploaded photo and identity document, which live
in this database (see `DatabaseDocumentStorage`). **Move to a paid plan
before real users depend on this.** On a paid plan Render keeps continuous
backups: restore is Dashboard -> the database -> Recovery -> Restore
Database, picking a timestamp at least ten minutes old, which provisions a
new instance at that point in time. The window is 3 days on Hobby and 7
days on Pro or higher.

Until then, the only backup that exists is one taken by hand, and it
should be taken before anything risky:

```bash
# External connection string from the Render dashboard.
pg_dump "$DATABASE_URL" -Fc -f sheout-$(date +%F).dump   # outside the repo
pg_restore -d "$NEW_DATABASE_URL" sheout-2026-09-15.dump # to restore
```

Paid plans also allow exporting a logical backup from the dashboard;
those exports are kept for seven days.

## What's intentionally not here yet

As of v1.0.0 every module in the table above is implemented and the core
loop - book, dispatch, pickup code, trip, payment, payout - runs on the
live deployment. What is deliberately left for later:

- Automated payouts. Operators send the money themselves and mark the
  request paid in the console - there is no payout API integration. TDS is
  not calculated or deducted either; a partner's PAN is collected and the
  console says whether one is on file, which is as far as it goes.
- Automated identity or face matching. Verification is a person reading a
  document, deliberately, and that is not changing.
- Lunch Box. The backend still accepts the `LUNCHBOX` category, but the
  apps do not offer it.
- Self-hosted routing. Fares and routes use the public OSRM demo server,
  which has no uptime guarantee; self-host once traffic justifies it.
- `users`' saved addresses using the `GeoAddress` shape `booking`
  introduced - home and work addresses are still plain text.
- CI/CD.
- An embedded/Testcontainers substitute for backend tests - the one
  context-load test needs a real local Postgres + Redis running (e.g.
  `docker compose up postgres redis`) to pass.

## Releases

Releases are tagged on GitHub when a meaningful batch of work has landed
**and been verified on the live deployment** - not per commit. One tag
covers the backend and both apps, because they ship together from this
repository. Versions follow semver: a new capability is a minor release
(`v1.1.0`), a fix to something already released is a patch (`v1.0.1`),
and a change that breaks existing API clients or the apps' contract with
the backend is a major one. Release notes describe what the app can do,
by area; the commit list is linked beneath them.

## Licence and use

**SheOut is proprietary. All rights reserved.** See [LICENSE](LICENSE).

This repository is public so the work can be read and evaluated. That is
the only thing it grants. Readable source is not public-domain source:
using, copying, modifying, deploying or selling any part of this requires
written permission. Ask, and it may well be given -
nandupanakanti@gmail.com.

**This repository will be made private before launch.** While it is
public, treat every commit as world-readable forever, including anything
pasted into a commit message. No credential has ever been committed here
and none should start now: real values live in the Render and Vercel
dashboards, and `.env` is gitignored.

[NOTICE](NOTICE) lists the third-party attributions that must be
preserved. The OpenStreetMap credit on the map is one of them - it is an
ODbL licence obligation, not decoration, and the Leaflet attribution
control that renders it must not be removed.

[SECURITY.md](SECURITY.md) is how to report a vulnerability. Privately,
by email, never as a public issue - this service is live and handles
women's home addresses, live locations and identity documents.

[CONTRIBUTING.md](CONTRIBUTING.md) explains why pull requests are not
accepted, and what is genuinely welcome instead.

### Still outstanding before launch

Every decision waiting on the company's registration - insurer, police
re-check period, GST, required documents, consent wording - is in
[docs/PENDING_DECISIONS.md](docs/PENDING_DECISIONS.md), with who must answer
it and which setting changes when they do.

The in-app privacy policy and terms
(`frontend/design-system/src/legal/content.ts`) are written against what
the code actually does, but carry visible `[to be completed by SheOut]`
placeholders for facts only a registered business can supply: the
registered entity and address, the grievance officer India's DPDP Act
requires of an intermediary, and the data retention periods the business
is willing to commit to. They are blank deliberately - inventing them
would turn an honest draft into a misrepresentation that real users rely
on. The copyright holder named in `LICENSE` and `NOTICE` should be
updated to the registered company at the same time.
