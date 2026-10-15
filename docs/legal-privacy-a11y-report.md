# Legal, privacy and accessibility: Phase 1 report

Branch `feat/legal-privacy-a11y` (from `origin/main` at `ae43f5a`). **Nothing
was pushed or deployed**, and no production data was read or changed. Sample
shops, test sign-ins and every other piece of data stay as they were.

Phase 0, the audit, is in `docs/legal-audit.md`. The decisions and inputs
still owed are in `docs/legal-open-items.md`. Screenshots are in
`docs/legal-privacy-a11y/` (360 and 430 px for the apps; desktop for the
console).

## 1. What was decided, and what was built

| Your answer | Built | Where |
|---|---|---|
| Remove every police- or background-verified claim | Removed from the marketing site (hero chip, safety copy, Safety list). CI now fails a build whose bundle says "police-verified", "background-verified", "100% safe", "trusted by N" or "lorem ipsum" | `frontend/marketing-site/src/content.ts`, `components/Safety.tsx`, `scripts/check-legal-sync.mjs` |
| Privacy Policy lists only live third parties, with the Singapore disclosure | One list, built from `legal-entity.config.json` (`processors[].live`). Live today: Render (Singapore), Vercel, Google Maps/Places, Google Fonts, Google Sign-In, Firebase push, Razorpay, AWS (Mumbai), Nominatim. Off until you confirm: Anthropic, Sentry, SMS. A "Where your data is stored" section names Singapore | `frontend/design-system/src/legal/content.ts`, `legal-entity.config.json` |
| Label screenshots "Sample screens" | A label on every phone mockup, the hero video and the seller video; burned into the share image (`og-image.jpg`) | `marketing-site/src/components/SampleLabel.tsx` |
| Grievance contact `grievance@sheoutride.com`, officer name to come (India-based) | In the config, every legal page, every footer, both apps' Privacy screens (server default). Name shows as a red placeholder | `legal-entity.config.json`, `application.yml` |
| Snivika Technology Private Limited; CIN and address to come | One `brandLine` string ("SheOut, a brand of Snivika Technology Private Limited") and one `legalName`, used everywhere. CIN and address show as red placeholders | same |
| Unticked consent checkbox; server Terms version = the version shown, no re-consent | The checkbox was already unticked and recorded. The sign-in line no longer says "by tapping you agree"; it is a notice. The server records `2026-10-02`, the version shown (was `2026-09-20`). CI checks the two match. Acceptance is recorded once, so nobody is asked again | `ConsentCheckbox.tsx`, `LegalConsentNotice.tsx`, `application.yml` (`LEGAL_TERMS_VERSION`) |
| Pause promotional messages until there is an opt-in | `PROMOTIONAL_MESSAGES_ENABLED=false`: the "we miss you" nudger does not run, and a promotional announcement is refused (409). Staff now mark each announcement Service or Promotional; service and safety notices still go out; waitlist messages still go (she asked) | `AnnouncementService`, `ReengagementNudger`, console compose |
| Refunds to wallet by default; documented path to the original method for failed, duplicate and SheOut-cancelled trips, timeline in config | Refund Policy states exactly that. `REFUND_TO_SOURCE_WORKING_DAYS=7`, mirrored in the policy config and checked equal by CI. Finance runbook. The console's Refund dialog says which cases go back to the original method and the deadline | `docs/refunds-to-source.md`, `AdminRefundPolicyController` |
| Retention jobs off by default | `RETENTION_ENABLED=false`; switched on, they start as a dry run (`RETENTION_DRY_RUN=true`) that only counts. Every run is logged in `retention_runs` | `privacy.internal.RetentionSweeper` |
| Replace illustrations of unknown licence | The four brand illustrations and the eight Marketplace category pictures are replaced by artwork drawn for SheOut (SVG). Register of every asset | `design-system/src/assets/art/*.svg`, `customer-app/src/assets/seller/*.svg`, `docs/assets-licences.md` |
| Behind flags; no push, no deploy | Done. Defaults are the safe ones listed in section 3 | |

## 2. Pages created or changed

All draft, pending legal review (said in the code). The page text is in English
with a note on hi/te ("available in English only for now"); every new button,
link and label is in en/hi/te.

| Page | Address (both apps) | New? |
|---|---|---|
| Privacy Policy | `/privacy` | Rewritten |
| Terms of Use (customers) | `/terms` | Corrected: road-route fares, today's SOS behaviour, no police claim |
| Partner Terms | `/partner-terms` | New |
| Refund and Cancellation Policy | `/refunds` | New |
| Cookie and Local Storage Policy | `/storage-policy` | New - says plainly why there is no banner |
| Parcel Rules | `/parcel-rules` | New - same list and version as the server (CI-checked) |
| Safety and Community Guidelines | `/safety-guidelines` | New |
| Grievance Redressal | `/grievance` | New - 48 h acknowledgement, 30 days to resolve |
| Contact and business details | `/contact` | New |
| Delete your account (public) | `/delete-account` | New - explains, then OTP sign-in (the identity check) to the delete step |

Every page ends with the business-details footer, and Settings in both apps
links to the pages relevant to it. The marketing footer links to all of them
on `app.sheoutride.com`. A production build warns about each missing business
detail, and **fails** when `LEGAL_STRICT_BUILD=true` - switch that on in Vercel
once the CIN, address, phone and officer name are in.

Screenshots: `customer-*-360/430.webp`, `partner-*-360/430.webp`,
`marketing-*-360/430.webp`.

## 3. Data minimisation, retention, flags

- **Nothing was removed** - you asked to keep all data, and no field the audit
  inventoried is unused. (One audit item was wrong: shift selfies were already
  deleted after 30 days, at her next shift check. The policy now says so.)
- **Document numbers masked in the console** for everyone except roles that
  review documents (they compare the number with the image, and opening an
  image is already logged). Bank numbers were already masked except for
  payout roles (`ConsoleMasking`) - another audit item that was half wrong.
- **Migrations, new files only:** `V82__retention_runs.sql`,
  `V83__court_record_screening.sql`. Nothing old was edited, nothing dropped.
- **Retention tasks** (all off; dry run first): chat 90 days (kept while a
  support dispute is open), shift photos 30 days, rejected-document images 30
  days, court screenings 30 days after a rejection and 365 days after she
  leaves.

| Flag | Default | Meaning |
|---|---|---|
| `LEGAL_STRICT_BUILD` | unset (off) | On: a build with missing business details fails |
| `LEGAL_TERMS_VERSION` | `2026-10-02` | The version recorded at sign-up |
| `GRIEVANCE_OFFICER_EMAIL` | `grievance@sheoutride.com` | |
| `PROMOTIONAL_MESSAGES_ENABLED` | `false` | Nudges and promotional announcements paused |
| `REFUND_TO_SOURCE_WORKING_DAYS` | `7` | The promise in the Refund Policy |
| `RETENTION_ENABLED` / `RETENTION_DRY_RUN` | `false` / `true` | |
| `RETENTION_DAYS_*` | 90 / 30 / 30 / 30 / 365 | Proposed periods |
| `ADMIN_COURT_SCREEN_ENABLED` | `false` | Court-record checks |
| `COURT_SCREEN_CONSENT_VERSION` | `2026-10-14-draft` | |
| `COURT_SCREEN_RESPONSE_DAYS` | `7` | Her time to answer |

## 4. Third parties and the cookie verdict

**No cookie banner, by evidence:** no SheOut site sets a cookie, and every
browser-storage key is needed for something she asked for (Phase 0, section C;
the Storage Policy lists them). No analytics, ads, session replay or
fingerprinting exist.

What still reaches Google before she does anything: **Google Fonts** (not
self-hosted - you did not answer O-16) and Google Maps once a map opens. Both
are named in the Privacy and Storage policies.

New headers on both apps (`vercel.json`): `X-Content-Type-Options`,
`Referrer-Policy`, `X-Frame-Options`, a `Permissions-Policy` limited to
location, camera and (customer) motion for shake-to-SOS, and a
**Content-Security-Policy in report-only mode**. Checked locally as if
enforced: no violation on sign-in, legal pages, Home or Settings other than
the local API address (production's API is allowed). Turn it from
`-Report-Only` to enforced after a preview deploy shows the browser console
clean with maps and Razorpay checkout open.

## 5. Claims and content

| Old | New | Why |
|---|---|---|
| "ID and police-verified partners" (hero) | "Every partner checked by a person" | Not true for every partner today |
| "ID with a live selfie, police verification, and her licence, RC and insurance reviewed..." | "Her ID with a live selfie, and her licence, RC and vehicle insurance, reviewed by a person..." | Same |
| Safety list "Police verification" | removed | Same |
| Terms: "subject to a police verification check" | "our team checks her ID, a live selfie, her driving licence and her vehicle papers" | Same |
| Terms: fare from "straight-line distance" | "the road route" | The code uses the road route |
| Terms/Privacy: SOS "alerts your contacts by SMS" | "opens your own SMS app with a message ready; SheOut will send it itself once its SMS service is approved" | No SMS provider yet |
| Privacy: "Twilio, for SMS" | removed until live | Not used |
| Sign-in: "By tapping Send OTP you agree..." | "Read our Terms and Privacy Policy. You will be asked to agree when you create your account." | Implied consent |

Fake or placeholder content: the marketing screenshots and videos (sample
data, a 4.9 rating) are labelled "Sample screens" - they still need a
re-shoot (open item). Sample shops are kept, labelled, as you asked. No lorem
ipsum, testimonials or user counts exist.

Left as they are, for the lawyer: "safe rides" (soft wording used across the
apps), the marketing site's "rider" wording, and "brings a spare helmet for
you" on the marketing Bike Taxi card (not something the app enforces).

## 6. Asset licences

`docs/assets-licences.md` lists every asset. Still unresolved: the marketing
screenshots/videos (show the old art; labelled), the 26 sample-shop photos
(kept on your instruction; source unknown), the logo's author, and the
edge-tts voices (do not publish voiced explainers).

## 7. Accessibility

| Run | Result |
|---|---|
| Phase 0, live sign-out pages | customer: `button-name` (critical), `target-size` (serious x2); partner: `target-size` (serious x2); console: `landmark-one-main`, `page-has-heading-one` |
| This branch, first axe run, customer app (96 screen x language x width cases) | 10 failed: sign-in `button-name` (6), SOS and Emergency contacts `color-contrast` in hi/te (4) |
| After | **customer 96/96, partner 78/78 pass** - no violation of any severity reported; every case also checks the screen really rendered (not a redirect to sign-in) |

Fixes: the "Sign Up"/"Login" hint links were empty buttons because the
translation used `<link>`, an HTML void tag - renamed in en/hi/te; inline
links are at least 24 px tall (WCAG 2.2, 2.5.8); the English line under
translated safety text was faded to 75% - **3.39:1 before, 5.87:1 after**
(AA needs 4.5:1); every app screen and the console sign-in now has a `main`
landmark, and the console sign-in an `h1`. Keyboard: a sign-in test uses the
keyboard alone (focus order, a visible focus ring, Enter sends the code). CI
runs all of it (`accessibility` job).

Not done: a keyboard-only test of booking (the map screens need a real map
key), and a screen-reader pass (NVDA/VoiceOver) - the brief asked for both.

## 8. Court-record checks

A manual, written-down screening of public court records. **A screening aid,
not police verification**: nothing sets a partner's police status, and every
screen says so. Off (`ADMIN_COURT_SCREEN_ENABLED=false`): every endpoint
answers 404 and partners see nothing.

- Consent first, its own versioned wording (`COURT_SCREEN_CONSENT`, a draft
  for the lawyer); she can withdraw, and the search stops.
- No automation: eCourts opens in a new tab; the reviewer writes down every
  search (district, name variant, date, outcome; a note for anything but "No
  match"; case type, status, year and court only for a match; an optional
  screenshot, privately stored, viewed by signed link and logged).
- A possible or confirmed match needs a second, different reviewer; nobody
  decides a case they flagged; before **any** rejection she is told and has
  `COURT_SCREEN_RESPONSE_DAYS` to answer or send a police clearance
  certificate. A rejection keeps a reason category, not the case.
- Every step is in the verification audit trail and the staff audit log; an
  approval becomes a `COURT_RECORD_SCREEN` check, so the weekly 10% second look
  covers it. Reports: a console summary and the `court_screening_report` view.
- Partner copy in en/hi/te: consent, "we are checking", "we need more
  information" (with an answer box), the decision, withdrawal.
- Built on the existing `CheckProvider` (the manual provider answers it), so a
  vendor can replace the manual work without changing the screens.

Tests: `CourtScreeningFlowTest` (state machine, audit, sampling, retention dry
run and real run), `CourtScreeningConsoleTest` (real console sessions:
permission, consent gate, second reviewer, hearing, own-case rule),
`CourtScreeningControllerTest` (partner 404s), the existing
`EveryRoleIsRefusedWhatItLacksTest` (every new console endpoint refuses roles
without `verification.review`), and a browser test of the reviewer flow
(`qa/console-e2e`). Screenshots: `console-court-*.webp`,
`partner-court-*.webp`.

Differences from the brief, on purpose:

- A staff role without `verification.review` gets the console's usual **403**
  (`PERMISSION_REQUIRED`), not 404 - the console's permission check answers
  before any endpoint runs, and a startup guard requires it on every
  endpoint. 404 is used where it hides something: feature off, no such
  partner or screening, partner endpoints for non-partners.
- Rejecting a screening does not by itself change her police status or stop
  her working; the reviewer records the police decision as today. Partners
  are not pushed a notification when the state changes; they see it on the
  Verification screen.

## 9. What you and the lawyer still owe

`docs/legal-open-items.md` - updated. The ones that block launch: the
officer's name, CIN, registered address and phone (the strict build will
not pass without them); the lawyer's review of every page and of the court
check; confirming Anthropic and Sentry are or are not live; a re-shoot of
the marketing screens.

## 10. Branch, commits, tests

Commits on `feat/legal-privacy-a11y`, none pushed, nothing deployed:

| Commit | What |
|---|---|
| `10836dc` | Phase 0 audit and open items |
| `c45b1a5` | Legal pages, business details config, claims, consent notice, Terms version |
| `707367e` | Promotional pause, refunds to source, retention jobs |
| `3e3b261` | New artwork, document-number masking, accessibility fixes and suites, security headers |
| `22487ff` | Court-record checks, incident-response runbook |
| (this commit) | Keyboard test, evidence screenshots, this report |

No placeholder legal text can reach production unnoticed: every missing
business detail is a red placeholder on the page, a warning in every build,
and a failed build once `LEGAL_STRICT_BUILD=true`.

Test results are in section 11 below.

## 11. Test results (2026-10-15, this machine)

| Suite | Result |
|---|---|
| Backend, full (`mvn test`, a fresh local database at V83) | 596 tests: **0 failures**, 13 errors - all in `ParcelProofOfDeliveryFlowTest` and `BusyPartnerGetsNoOfferTest`, all `ERR unknown command 'GEOADD'` from Memurai, the local Redis stand-in. CI runs real `redis:7`. None of these tests touches this branch's code |
| Court-record checks (`CourtScreeningFlowTest`, `CourtScreeningConsoleTest`, `CourtScreeningControllerTest`) | 9 / 9 |
| Every console endpoint refuses roles without its permission (`EveryRoleIsRefusedWhatItLacksTest`) | 3 / 3, new endpoints included |
| Retention, promotional pause, migrations (`RetentionSweeperTest`, `ChatRetentionTaskTest`, `PromotionalPauseTest`, `MigrationPathTest`) | all pass |
| Customer app: axe on 8 public + 8 signed-in screens x en/hi/te x 4 widths, and keyboard sign-in | 97 / 97 |
| Partner app: axe on 6 public + 7 signed-in screens | 78 / 78 |
| Console court-record flow in a browser (`qa/console-e2e`) | 1 / 1, no page errors |
| Legal sync and bundle claims check (`scripts/check-legal-sync.mjs`) | pass, 3 bundles scanned |
| Type checks and production builds, all three frontends | pass (with the four business-detail warnings) |
| Dependencies | npm: 4 high in `firebase` (Firestore/gRPC, **not in either bundle** - checked), 2 moderate in `react-router-dom` (open redirect via `Link`; sign-in's own redirect is allow-listed). Maven OWASP check (database dated 2026-09-13): 43 critical / 111 high, almost all from Spring Boot 3.3.4's libraries - open item O-22 |
