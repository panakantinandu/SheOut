# Legal, privacy and accessibility audit (Phase 0)

Branch `feat/legal-privacy-a11y`, cut from `origin/main` at `ae43f5a`. Read-only:
nothing in the apps, the database or production was changed to write this.

**This is not legal advice.** It is an engineer's inventory of what the code
and the live sites actually do, written so a lawyer can review it quickly.
Every legal conclusion below is marked for that review.

How the evidence was gathered (2026-10-14):

- **Code:** file and line references, relative to the repo root.
- **Live run:** a Playwright run of the four public, signed-out entry points
  (marketing site, customer app, partner app, staff console). It recorded every
  request host, cookies, web storage, IndexedDB, service-worker caches, the
  document's security headers, and axe-core 4.10.2 results at 360, 375, 390 and
  430 px. The script and its output are in `D:\_qa-scratch\legal-audit` (outside
  the repo): `capture.cjs`, `out/report.json` and `out/*.png`. Nothing was
  submitted and nobody was signed in.
- **Vercel API:** the names (not the values) of the environment variables set
  on each frontend project, which show which optional integrations are on in
  production.
- **Official sources** for the DPDP dates: the Rules as published in the
  Gazette (G.S.R. 846(E), 13 November 2025), downloaded from meity.gov.in, and
  PIB's 17 November 2025 explainer. Both are quoted in section G.

Facts that frame everything below, as the founder states them:

- The parent company, Snivika Technology Private Limited, is registered. The
  exact spelling needs confirming: see open item O-1.
- SheOut is a brand. The subsidiary, its bank account, DLT registration and
  insurance are not done yet.
- Razorpay runs on test keys and no SMS provider is live.
- Sample shops and test sign-ins on Render stay as they are: this pass removes
  no data.

---

## Summary table

Severity measures the harm if SheOut launched to the public as things are
today. Several items are only Critical at launch: during closed testing they
are lower.

| # | Issue | Severity | Evidence | Proposed fix | Lawyer? | You? |
|---|---|---|---|---|---|---|
| 1 | Privacy Policy's list of third parties is incomplete, yet it claims to be complete. Google Maps/Places, Google Fonts, Firebase push, Anthropic, Render (Singapore) and Vercel are missing. Twilio is listed but not used | **Critical** | `frontend/design-system/src/legal/content.ts:103-113`; Vercel env (`VITE_GOOGLE_MAPS_API_KEY` set on both apps); live run hosts; `render.yaml:18` | Rewrite the list from the inventory in section C | Y | N |
| 2 | All personal data, including ID and selfie images, is stored in **Singapore** (Render Postgres, `DOCUMENT_STORAGE_PROVIDER=database`). The policy implies India ("AWS Mumbai") | **High** | `render.yaml:14-31`; `backend/src/main/resources/application.yml:379` | Disclose the cross-border transfer now. Decide whether to move the database or documents to an Indian region later (not in this pass) | Y | Y |
| 3 | Sign-in screen still says "By tapping Send OTP you agree…": consent implied by using the product | **High** | Live screenshot `out/customer-360.png`; `frontend/design-system/src/i18n/en.json:16` | Keep the unticked checkbox at sign-up (`ConsentCheckbox.tsx`). Change the sign-in line to a notice ("Read our Terms and Privacy Policy") | Y | N |
| 4 | The consent version saved on the server differs from the version users are shown: server `2026-09-20`, app `2026-10-02` | **High** | `backend/.../auth/internal/AuthService.java:53` (default, not set in `application.yml`) vs `content.ts:47` | Use a single version, set in config and returned by the API. The app shows what the server will record | N | N |
| 5 | No marketing consent exists. "We miss you" pushes and staff announcements (push **and email**) go to everyone | **High** | `notifications/internal/ReengagementNudger.java`; `notifications/internal/AnnouncementService.java:143`; no column in any migration | Separate, unticked opt-in for promotional messages. Service messages stay as they are | Y | Y |
| 6 | Marketing site says **"ID and police-verified partners"**. The code enforces a police status, but an operator can record it from a third-party BGV and you say police verification is not fully done in practice | **High** | `frontend/marketing-site/src/content.ts:31,109,139`; `driververification/internal/VerificationRecordEntity.java:301-307`; `PoliceVerificationMethod.java:15-19` | Reword until every live partner has a police certificate on file (wording proposed in D-1) | Y | Y |
| 7 | Marketing screenshots and video were made with mocked data, including a **4.9 rating** and invented trip history, shown with no "illustrative" label | **High** | Memory of how the shots were made (record/replay, rating set to 4.9); `frontend/marketing-site/public/screens/*.webp`, `public/media/hero-loop.*` | Re-shoot without a rating, or label every frame "Illustrative screen, sample data" | N | Y |
| 8 | Partner Terms do not exist. Customer Terms speak to "driver-partners" in places but there is no partner agreement, no independence clause reviewed, no payout terms | **High** | `content.ts:155-246` (one Terms document) | Separate Partner Terms (Phase 1). Classification wording needs the lawyer | Y | Y |
| 9 | No Refund and Cancellation Policy page. Refunds go **only to the SheOut wallet**, never back to the card or UPI | **High** (Razorpay activation, E-Commerce Rules) | `admin/internal/web/AdminRefundController.java:47-52` | Policy page stating the real rule, plus a decision on refund to source (open item O-12) | Y | Y |
| 10 | Grievance officer, registered entity, address and CIN are not shown anywhere. `GRIEVANCE_OFFICER_EMAIL` is blank by default | **High** (E-Commerce Rules r.4, DPDP r.9) | `application.yml:164-168`; `content.ts:126,150-153,241` | `legal-entity.config` + Contact page + footer; production build fails while values are missing | Y | Y |
| 11 | Legal pages are English only, but the apps run in en/hi/te. Marketing site is English only | **Medium** | `content.ts` has one language; `marketing-site/src/content.ts` | Translate after the lawyer approves the English (machine drafts marked as such before then) | Y | N |
| 12 | Licence, RC, policy, police-certificate and **bank account numbers** are stored in plain text and shown in full in the console. Viewing a bank number is not logged | **Medium** | `V55__partner_documents.sql:23`; `V18__payouts.sql:13-15`; `static/admin/index.html:3274,4561,4952,8778` | Mask by default (last 4), reveal with reason, log the reveal. Column encryption: propose in Phase 1 | N | N |
| 13 | No retention rule for chat messages, trip pickup/drop coordinates, rejected verification documents, SOS locations, sessions, or inactive accounts | **Medium** | Only `ParcelRetentionSweeper` (90 days) and Redis TTLs exist (`DriverLocationStore.java:48`) | Retention jobs, off by default, dry-run first (numbers in O-8) | Y | Y |
| 14 | Customer and partner apps send **no** Content-Security-Policy, Referrer-Policy, Permissions-Policy, X-Content-Type-Options or X-Frame-Options. Session token is in `localStorage` | **Medium** | Live run headers; `customer-app/src/api/client.ts:83` | Headers in `vercel.json` for both apps (CSP first in report-only mode) | N | N |
| 15 | Google Fonts are loaded from Google's servers on every page, so visitor IP addresses reach Google before anyone signs in | **Medium** | Live run: `fonts.googleapis.com`, `fonts.gstatic.com` on all three Vercel sites | Self-host the fonts (SIL OFL allows it) | N | Y |
| 16 | Terms say the fare is from "straight-line distance". The code now prices by road route (OSRM) | **Medium** | `content.ts:195`; `application.yml` routing keys | Correct the sentence | N | N |
| 17 | Terms describe SMS to emergency contacts as working. SMS is blocked until DLT approval, so today SOS opens the customer's own SMS app | **Medium** | `content.ts:208`; `docs/PENDING_DECISIONS.md` #25-26 | Say what happens today; update when DLT goes live | Y | N |
| 18 | No public account-deletion URL (Google Play needs one if a Play listing is ever made). In-app deletion exists | **Medium** | `privacy/internal/AccountDeletionService.java`; no public route in `customer-app/src/App.tsx` | Public `/delete-account` page with OTP check | N | N |
| 19 | Accessibility: an unnamed "Sign Up" button on customer sign-in (critical); two 18 px-tall links on both apps' sign-in (serious, WCAG 2.2 2.5.8); no `<main>` landmark on any app | **Medium** | Live axe run, section E | Fix plus axe in CI | N | N |
| 20 | Origin and licence of the four illustrations (`design-system/src/assets/art/*.webp`) and of the edge-tts voices are not recorded | **Medium** | `docs/THIRD_PARTY_LICENSES.md` covers 2 libraries only | `docs/assets-licences.md`; you confirm each source | N | Y |
| 21 | Firebase JS SDK pulls in 4 high-severity advisories (Firestore and gRPC, which the apps do not use) | **Low** | `npm audit --omit=dev`: 4 high, 2 moderate | Update `firebase`, or import only `firebase/messaging` | N | N |
| 22 | Owner bootstrap logs the owner's email (redacted) and, without email set up, the invite link at INFO | **Low** | `staff/internal/OwnerBootstrap.java:94-100` | Already redacted. Leave as is, note it in the runbook | N | N |
| 23 | Marketing site links point to `app.` / `partner.sheoutride.com`, which have no DNS records yet | **Low** (pending your GoDaddy records) | DNS check 2026-10-14 | Your GoDaddy records (already sent) | N | Y |

Things that are **already right**, so nobody spends time on them:

- 18+ is enforced from the date of birth on both roles: `users/internal/ProfileRules.java:20,50`.
- Unticked Terms checkbox at sign-up, with time and version saved: `design-system/src/components/ConsentCheckbox.tsx`, `V28__terms_acceptance.sql`.
- Partner verification consent is versioned, and the server refuses uploads without it: `content.ts:249-300`, `V56`.
- Self-service data export and account deletion (anonymisation, with financial records kept): `privacy/internal/*`.
- Phone numbers are redacted in logs and Sentry: `sharedkernel/logging/Redact.java`, `platform/SentryConfig.java`. `send-default-pii` is off.
- Customer ID: she is invited to cover the Aadhaar number, and no government ID **number** is stored for anyone (image only): `customer-app/src/screens/Verification.tsx:40-47`, `PartnerDocumentType.java`.
- Document links are signed and expire in 15 minutes, and every view by staff is recorded: `application.yml:390`, `verification_audit_events`.
- Parcel photos and receiver numbers are deleted after 90 days unless disputed: `booking/internal/ParcelRetentionSweeper.java`.
- Insurance copy appears only when an active policy exists: `customer-app/src/components/InsuredTripsExplainer.tsx:23`, `driver-app/src/screens/Earnings.tsx:339`.
- Sample shops carry a "Sample shop" badge: `customer-app/src/i18n/en.json:824-826`. **Kept**, as you asked.
- Staff console: strict hash-based CSP, HSTS, `no-referrer`, `X-Frame-Options: DENY` (live headers).
- No secrets found in the repo (`git grep` for live Razorpay, AWS, Google, GitHub and private-key patterns: none).
- No analytics, ad pixels, session replay, heatmaps or fingerprinting anywhere (no such package in any `package.json`, no such host in the live run).

---

## A. Data inventory

Verdict key: **needed** (the service breaks without it), **optional** (useful,
but she should be able to leave it out), **remove** (stored, but no feature
needs it).

| Field | Collected where | Purpose | Who sees it | Stored | Retention today | Verdict |
|---|---|---|---|---|---|---|
| Phone number | Sign-in (`PhoneField.tsx`) | Login, trip contact | The other party on an active trip (call button); staff | `accounts.phone_number` (`V1:3`) | Until deletion, then unlinked | needed |
| Name | Complete profile | Shown to the other party | Other party, staff | `customer_profiles.name`, `driver_profiles.name` (`V2`) | Until deletion, then "Deleted User" | needed |
| Email | Profile; Google sign-in | Receipts, seller reminders, announcements | Staff | `accounts.email` (`V4:11`), profiles (`V20:12,16`) | Until deletion | **optional** for customers (it is in the profile form, `ProfileCompletionForm.tsx:166`). Confirm it is not required |
| Date of birth | Complete profile | 18+ check | Staff | profiles (`V20:11,15`) | Until deletion | needed (consider keeping only "18+ confirmed" plus the year, see O-9) |
| Gender | Not collected as a field. A reviewer decides from the ID | Women-only service | Staff | `verification_records.gender_verification_status` | Until deletion | needed |
| Government ID image (customers and partners) | Verification screen | Women-only check | Verification staff, signed links, each view logged | Postgres (`DatabaseDocumentStorage`) in **Singapore** | While verified, deleted with the account | needed |
| Government ID number | Not stored (image only; Aadhaar maskable) | - | - | - | - | - |
| Live selfie (verification) | Verification | Match ID to person | Verification staff | `verification_records.selfie_document_key` (`V34:17`) | Until deletion | needed. **Retention after verification: propose** (O-8) |
| Shift selfie (partners) | Go online | Same person as verified | Staff; AWS Rekognition (Mumbai) compares | `shift_checks.selfie_key` (`V47:12`) | **No deletion job** (only the 12-hour validity sweep) | needed, with a retention rule (propose 30 days) |
| Face embeddings | Computed **on the phone** by face-api.js (`driver-app/src/lib/faceMatch.ts:31-44`), not stored. The server stores only a distance or similarity score (`V47:15-16`) | Shift check | - | score only | Until deletion | needed (score). The policy must say face matching happens |
| Licence, RC, insurance, PUC, police-certificate numbers | Partner documents | Legal checks for carrying passengers | Verification staff, **in full** | `partner_documents.document_number` (`V55:23`), `police_certificate_number` (`V56:17`) | While a partner, deleted with the account | needed. **Mask in the console** |
| Flagged document numbers | Staff | Stop re-use of a fraudulent document | Staff | `flagged_document_numbers` (`V75:17`) | Forever | needed. Lawyer: retention of a fraud list |
| Vehicle registration number | Partner onboarding | Shown to the customer to identify the vehicle | Customer on a trip, staff | profile | Until deletion | needed |
| Bank account number, IFSC, UPI ID (partners, sellers) | Payout setup | Pay partners | Staff, **in full** (`static/admin/index.html:3274,3276,8778`); the partner app gets it masked (`driver-app/src/api/types.ts:431`) | `payout_accounts` (`V18:13-15,70-72`), plain text | Until deletion | needed. **Mask in the console, log reveals** |
| Customer card or UPI details | Razorpay checkout | Payment | Razorpay only | Not stored by SheOut | - | - |
| Pickup/drop coordinates and addresses | Booking | The trip | Partner on the trip, staff | `bookings` (`V3:10-15`), `booking_destination_changes` (`V37`) | **Forever** | needed. Retention rule (propose: precise coordinates rounded after 12 months, record kept for tax) |
| Saved home/work addresses | Saved addresses | Convenience | Her | `customer_profiles` (`V2:5-6`, `V29`) | Until deletion | optional (already optional) |
| Partner live GPS | While online | Matching, live tracking | Customer on her trip, staff | **Redis only**, no history (`DriverLocationStore.java:41-48`, 6-hour TTL on the timestamp) | Hours | needed |
| Customer last position | SOS (`sheout_last_position`, `customer-app/src/lib/sosDelivery.ts:38`) | SOS when GPS is slow | Her device | Browser `localStorage` | Until overwritten | needed (safety) |
| SOS alerts (location, time) | SOS | Safety response | Safety staff | `sos_alert` (`V6:24-25`) | **Forever** | needed. Retention rule (propose 3 years, see O-8) |
| Emergency contacts (third parties' numbers) | Emergency contacts | SOS | Her, staff | `emergency_contacts` | Until deletion or removal | needed |
| Chat messages | Trip chat | Coordination | The two parties, staff on a dispute | `chat_messages` (`V9`) | **Forever** (deleted with the account) | needed. Retention rule (propose 90 days after the trip, unless disputed) |
| Calls | Phone dialler | - | - | Not logged by SheOut | - | - |
| Parcel photos, receiver name and phone | Parcel flow | Proof of delivery | Sender, partner, staff | `parcel_photos`, `parcel_deliveries` (`V80`) | 90 days unless disputed (`application.yml:412`) | needed |
| Push tokens, user agent | App start | Push notifications | Staff (not shown) | `push_devices` (`V19:67-68`) | Until deletion | needed |
| Session device label and user agent | Sign-in | "Your devices" screen | Her | `account_sessions` (`V27:21-22`) | Up to 30 days | needed |
| Install ID | First run (`design-system/src/lib/referral.ts:17`) | Referral fraud check | - | `localStorage`, `referral_devices` | Until deletion | needed while referrals run |
| IP addresses | Staff sign-in and audit only | Staff security | Owners | `staff_sessions`, `staff_audit_events` (`V61:106`, `V62:30`) | **Forever** | needed. Retention (propose 1 year, the DPDP r.6 minimum for logs) |
| Customer IP addresses | Not stored. Rate limiting holds them in memory or Redis only | - | - | - | - | - |
| Support tickets | Help | Support | Staff | `support_tickets` | Until deletion | needed |
| Ratings and tags | After a trip | Quality | Staff (aggregates to partners) | `ratings` | Forever | needed |
| Device/booking event "device" | Booking events (`V53:15`) | Debugging | Staff | `booking_events` | Forever | **check**: if it is a user agent, set a retention |
| Logs | Server | Operations | Engineers | Render logs | Render's retention | Phone numbers redacted (`Redact.java`). No raw tokens found in log calls (grep of `log.*(phone|email|token|otp)`). OK |

Sensitive items: ID images, selfies and face scores, precise location, bank
details, SOS records. Today none of them is encrypted beyond what Render's
database applies at rest (provider disk encryption). No application-level
encryption exists for document numbers or bank numbers (`grep Cipher`: only
staff secrets, `staff/internal/StaffSecrets.java`).

---

## B. Consent and notices

| Question | Finding | Evidence |
|---|---|---|
| What does she agree to, and how? | Sign-up: an unticked checkbox for Terms + Privacy, with time and version saved. Sign-in: the line "By tapping Send OTP you agree…" is still there, which is implied consent | `ConsentCheckbox.tsx`; `V28`; live `customer-360.png` |
| Pre-ticked? | No | `ConsentCheckbox.tsx` (`checked` starts false in `CompleteProfile.tsx`) |
| Policy linked? | Yes, opens in-app | `ConsentCheckbox.tsx` |
| Version saved matches what she saw? | **No** (issue 4) | `AuthService.java:53` vs `content.ts:47` |
| Language saved? App version? IP hash? | No | `V28` has time and version only |
| Notice before location | Customer: location is read when she asks for it (pickup, SOS). Partner: watched while online. The browser prompt is the first thing she sees: no explanation screen found before `getCurrentPosition`/`watchPosition` | `customer-app/src/lib/geocode.ts:226`, `driver-app/src/lib/LocationBroadcastContext.tsx` |
| Notice before camera, selfie or face check | The partner verification consent covers ID and selfie review. **Face matching (on-device and AWS Rekognition) is not named** in that consent text | `content.ts:263-299` |
| Notice before parcel photos | Parcel rules sheet shown before booking. Photos are taken by the partner, and the sender is told | `ParcelDetailsSheet.tsx` |
| Notifications | The browser prompt only | `design-system/src/lib/push.ts` |
| Contacts access | Not used (emergency contacts are typed in) | - |
| Marketing consent separate? | **No marketing consent at all** (issue 5) | - |
| 18+ confirmed? | Yes, from the date of birth, server-enforced | `ProfileRules.java:50` |

---

## C. Cookies, storage, analytics, third parties

### C-1. Storage (all origins)

No cookies are set by any SheOut site (live run: `cookies: []` on all four).
The apps sign in with a bearer token in `localStorage`, so there is no session
cookie, which also means no CSRF cookie is needed.

| Key | Where | Purpose | Lifetime | Strictly necessary? |
|---|---|---|---|---|
| `sheout_access_token`, `sheout_account_id` / `sheout_driver_access_token`, `sheout_driver_account_id` | localStorage | Keep her signed in | Up to 30 days (JWT expiry, `application.yml:325`) | Yes |
| `sheout_language`, `sheout_language_pending_sync` | localStorage | Language | Until changed | Yes |
| `sheout_theme`, `sheout.admin.theme` | localStorage | Light/dark | Until changed | Yes (preference she set) |
| `sheout_active_trip`, `sheout_live_trip_resumed`, `sheout_rider_confirmed_<id>`, `sheout_partner_confirmed_<id>` | localStorage | Resume a live trip | Trip | Yes |
| `sheout_sos_contacts`, `sheout_sos_queue`, `sheout_last_position`, `sheout_discreet_sos` | localStorage | SOS works offline or with slow GPS | Until overwritten | Yes (safety) |
| `sheout_push_token`, `sheout_driver_push_token` | localStorage | Push | Until deletion | Yes (once she allows notifications) |
| `sheout_install_id`, `sheout_referral_code` | localStorage | Referral fraud check | Until cleared | Necessary for referrals |
| `sheout_nav_voice`, `sheout_fee_explained_seen`, `sheout_home_map_loads`, `<key>.dismissedAt` | localStorage | Remember dismissed hints, voice setting | Until cleared | Yes (functional) |
| `sheout_splash_shown`, `sheout_session_started`, `sheout_session_ended` | sessionStorage | Show the splash once per visit | Tab session | Yes |
| `workbox-precache-v2-…` | Cache Storage | Offline app shell (PWA) | Until update | Yes |
| Firebase messaging IndexedDB (after she allows push) | IndexedDB | Push token | Until deletion | Yes (once allowed) |

### C-2. Third parties actually contacted

| Third party | Company / country | When | What leaves the browser or server | In the Privacy Policy today? |
|---|---|---|---|---|
| Google Fonts | Google, USA | Every page load, all three Vercel sites, before sign-in | IP address, user agent, referrer | **No** |
| Google Maps JS + Places | Google, USA | Customer and partner apps once a map or search opens (key set on Vercel) | IP, map viewport, typed search text, session token | **No** (the policy names OpenStreetMap) |
| Nominatim (OpenStreetMap) | OSM Foundation, UK | Reverse geocoding fallback (`customer-app/src/lib/geocode.ts:22`) | Coordinates or search text, IP | Yes |
| Google Sign-In | Google, USA | Customer app, only if she taps it (`VITE_GOOGLE_CLIENT_ID` set) | OAuth | Yes |
| Firebase Cloud Messaging | Google, USA | After she allows notifications | Push token; server sends notification text | **No** |
| Razorpay | Razorpay, India | Checkout (`checkout.razorpay.com`) | Amount, order, contact as Razorpay asks | Yes |
| AWS Rekognition | Amazon, India (ap-south-1) | Partner shift check, if `FACE_CHECK_ENABLED` | Two selfies | Yes |
| Anthropic | Anthropic, USA | Help assistant and marketplace search, if `ANTHROPIC_API_KEY` is set | Typed messages, listings | **No** (`docs/LEGAL_REVIEW.md` already asks for it) |
| Render | Render, servers in **Singapore** | Always | Everything (database, Redis, documents) | "Our hosting providers", no country |
| Vercel | Vercel, USA (global edge) | Always | IP, request logs | **No** |
| Sentry | Functional Software, USA | Only if `SENTRY_DSN` / `VITE_SENTRY_DSN` is set. **Not set on Vercel**; Render dashboard unknown | Scrubbed error events | **No**. Check the Render dashboard (O-14) |
| Twilio | Twilio, USA | **Not used** (no keys) | - | **Listed**, but SMS is not live |
| OSRM | Self-hosted (`infra/osrm`) | Routing | Coordinates, server to server | n/a (SheOut's own) |
| `wa.me`, `maps.google.com`, `pvc.tspolice.gov.in` | WhatsApp, Google, Telangana Police | Only when she taps a link | Navigation | Plain links. Add `rel="noopener noreferrer"` |

### C-3. Cookie banner verdict

**No banner is needed.** No SheOut site sets a cookie, and every storage key
above is needed for something she asked for (sign-in, language, an active
trip, SOS, push after she allowed it). There is no analytics, ad pixel, tag
manager, session replay, heatmap or fingerprinting (no such package in any
`package.json`, no such host in the live run).

The two leaks are third-party loads that are not "tracking" but still send
her IP to Google before she does anything: **Google Fonts** (fix by
self-hosting) and Google Maps when a map opens (needed for the service, so it
gets disclosed in the policy instead). The Cookie and Storage Policy should
state this plainly. Lawyer: confirm that DPDP needs no separate consent for
these.

---

## D. Claims, fake content, assets

### D-1. Claims

| Claim | Where | True today? | Verdict | Proposed wording (for your review) |
|---|---|---|---|---|
| "ID and police-verified partners" | `marketing-site/src/content.ts:31` | The code requires a police status before a partner can work, but in operations not every partner has one, and a third-party BGV can be recorded as the police method | **reword** | "Every partner's ID, selfie and licence checked by a person" (put "police-verified" back once every live partner has a certificate on file) |
| "ID with a live selfie, police verification, and her licence, RC and insurance reviewed by a person before her first trip" | `marketing-site/src/content.ts:109` | Same as above | **reword** | Drop "police verification" until it is true for every partner |
| "pass police verification" (partner requirements) | `marketing-site/src/content.ts:139` | It is a requirement | **keep** | - |
| "Driver-partners are additionally subject to a police verification check" | `content.ts:176` (Terms) | A requirement, so true | **keep** (lawyer to check the wording) | - |
| "Fixed fare before you book" | `marketing-site/src/content.ts:31` | Final fare = quoted fare (`BookingService.java:654,697`); changes only if she changes the drop | **keep, qualify** | "Fare fixed before you book (it changes only if you change the drop)" |
| "SOS on every trip" | `marketing-site/src/content.ts:31` | The button exists on every trip. Texts to contacts are not sent by SheOut until DLT | **keep, qualify** | Keep the chip. On the Safety section: "SOS alerts our safety team and opens a text to your contacts" (until DLT) |
| "The SOS button alerts your emergency contacts by SMS" | `content.ts:208` | Not today (no SMS provider) | **reword** | As above |
| "Rides by women, for women" | Hero | Enforced: women-only verification | **keep** (lawyer on the eligibility wording, G) | - |
| "based on the straight-line distance" | `content.ts:195` | False now (road route) | **fix** | "based on the road route between your two points" |
| "Twilio, for SMS" | `content.ts:107` | Not used | **remove** until a provider is live | - |
| "Every SheOut trip is insured" | `customer-app/src/i18n/en.json:1512` | Shown only when a policy is active | **keep** (gated). Lawyer to check "every" | - |
| "Lowest price" | `customer-app/src/i18n/en.json:592` | A filter label, not a claim | keep | - |

No "100% safe", "24/7", "fastest", "cheapest", "guaranteed" or "trusted by N"
claims were found in en, hi or te (grep across all three apps' `i18n` and the
marketing site).

### D-2. Fake or placeholder content

| Item | Where | Shown in production? | Action |
|---|---|---|---|
| Mocked screens with a 4.9 rating, invented trips and names | `marketing-site/public/screens/*.webp`, hero video | **Yes**, on the live marketing site | Re-shoot with no rating, or label each frame "Illustrative, sample data" (issue 7) |
| Sample shops | Backend `sample-shops`, `MARKETPLACE_SAMPLE_SHOPS=true` | Yes, badged "Sample shop" | **Keep** (your instruction). Turn off at launch (`PENDING_DECISIONS.md` #28) |
| "e.g. Priya" placeholder | `customer-app/src/i18n/en.json:1075` | An input hint | Fine |
| Phone placeholder "98765 43210" | Sign-in | An input hint | Fine (a common example; not a real person's number on record) |
| `[to be completed by SheOut]`, `[to be reviewed by lawyer]` markers | `content.ts` | **Yes**, visible in the app's legal pages | Intended for now. Phase 1: production build fails while any remain |
| Lorem ipsum, testimonials, user counts | - | None found | - |
| Ratings "5.0" when there are none | - | No fallback to 5 found (`grep (rating)…(??|\|\|) 5`) | - |

### D-3. Assets

| Asset | Source | Licence | Status |
|---|---|---|---|
| `design-system/src/assets/art/{women,bike-taxi,parcel,marketplace}.webp` | Not recorded (commits `2b29ec5`, `5c736cd`, `9a77695`) | **Unknown** | **You confirm**: who made them and under what terms. They are illustrations, not photos of real people |
| `marketing-site/public/screens/*.webp` | Screenshots of SheOut's own apps | Own work | Fine, apart from the mocked data (D-2) |
| `marketing-site/public/media/hero-loop.*`, `sell-on-sheout.*` | Built from those screenshots (`D:\SheOut-Marketing\video-build`) | Own work | Fine. The Google Maps tiles in the frames need Google's attribution kept visible in each frame (check) |
| Explainer voices (if published) | Microsoft edge-tts neural voices | **Check**: edge-tts is an unofficial client of Microsoft's read-aloud service, and commercial use may not be allowed | Do not publish voiced explainers until checked (use Azure TTS under a commercial licence, or a recorded voice) |
| `Logo.jpeg`, app icons, Workspace logos in `public/` | SheOut's own | Own work | Fine. Trademark filing is a lawyer item |
| Fonts (Google Fonts) | Google Fonts | SIL OFL | Fine to self-host |
| Icons (`lucide-react`) | Lucide | ISC | Fine |
| face-api.js models (`driver-app/public/models/face`) | @vladmandic/face-api | MIT | Fine. Record it |
| `bot-avatars`, `thinking-orbs` | Recorded | MIT | Fine (`docs/THIRD_PARTY_LICENSES.md`) |

No other brand's logo was found in the repo's images (file names and the
marketing site were reviewed. A pixel-level review of the four illustrations
is part of your confirmation).

### D-4. Maps

Google Maps draws its own attribution and terms link, and the code does not
hide them (no CSS hides `.gm-style-cc` or similar: grep found none). Google
Places results must show "Powered by Google" when shown without a Google map:
**check** the destination search list in `customer-app` (Phase 1).

### D-5. Business details shown to users today

Shown: support email `support@sheoutride.com` (marketing footer). Make sure
that mailbox exists in Workspace (O-3).

Missing: legal entity name, CIN, registered address, grievance officer name
and contact, phone. The legal pages show `[to be completed by SheOut]`.

---

## E. Accessibility

What was tested: an automated axe-core run on the four signed-out entry
points at 360/375/390/430 px (identical results at every width). The
signed-in screens (booking, trip, SOS, parcel, partner onboarding, console
pages) need the mocked-API harness. That run, plus keyboard-only and
screen-reader passes, is in Phase 1 as part of the CI axe suite, so the
before/after numbers come from one tool. Nothing below about signed-in
screens is claimed as tested.

| Site | Violations (axe) | Detail |
|---|---|---|
| Marketing | **none** | - |
| Customer sign-in | `button-name` (critical, 1), `target-size` (serious, 2), `landmark-one-main` (moderate), `region` (moderate, 12) | The "Sign Up" link inside "New to SheOut? Tap Sign Up above…" renders as an empty `<button>` with no name (`.mt-6 > .underline`). The Terms/Privacy links in the sign-in notice are 18 px tall (`.leading-relaxed > .underline`) |
| Partner sign-in | `target-size` (serious, 2), `landmark-one-main`, `region` | Same Terms/Privacy links |
| Staff console sign-in | `landmark-one-main`, `page-has-heading-one`, `region` (moderate) | - |

From the code (not yet run):

- `lang` follows the chosen language (`design-system/src/i18n/index.ts:98`). Good.
- The OTP field uses `autocomplete="one-time-code"` (`OtpCodeField.tsx:58`). The phone field uses `type="tel"`, `inputmode="numeric"` and `autocomplete="tel-national"` (`PhoneField.tsx:54-61`). Good.
- Reduced motion is respected in 53 files (`motion-safe`, `prefers-reduced-motion`). Spot-check in Phase 1.
- The consent checkbox hides the real `<input>` with `sr-only` inside a `<label>`. Accessible, but the visible box has no focus ring of its own. Check in Phase 1.
- The map has no text alternative for pickup, drop, ETA and the partner's position. The trip screens show those as text beside the map; confirm in the screen-reader pass.
- SOS countdown announcement: not verified yet (Phase 1).

---

## F. Legal pages and flows

| Page or flow | Exists? | Linked from | Languages |
|---|---|---|---|
| Privacy Policy | Yes, `/privacy` in both apps | Marketing footer (to `app.sheoutride.com/privacy`), sign-up checkbox, sign-in notice, app Settings | **en only** |
| Terms (customers) | Yes, `/terms` | Same | **en only** |
| Partner Terms | **No** | - | - |
| Refund and Cancellation | **No** | - | - |
| Cookie and Storage Policy | **No** | - | - |
| Parcel Rules | In the booking sheet (server-versioned), not a page | Parcel booking | en/hi/te |
| Safety / Community Guidelines | **No** page (some text in Terms "What we expect of you") | - | - |
| Grievance Redressal | **No** page. Email shown in Help and Privacy screens if configured | - | - |
| Contact / business details | **No** | - | - |
| Partner verification consent | Yes (in-app, versioned) | Partner verification | en only |
| Account deletion, in-app | Yes | Profile, Privacy & Data | en/hi/te UI |
| Account deletion, public URL | **No** | - | - |
| Data download | Yes, in-app | Privacy & Data | en/hi/te UI |
| Consent withdrawal | Only by deleting the account | - | - |
| Correction | Profile editing. Partners' verified fields go through review | Profile | en/hi/te |

---

## G. Risk register (ranked)

### G-1. DPDP Act 2023 and Rules 2025: what applies when

From the Gazette text (G.S.R. 846(E), "New Delhi, the 13th November, 2025"),
Rule 1:

> (2) Rules 1, 2 and 17 to 21 shall come into force on the date of their
> publication in the Official Gazette.
> (3) Rule 4 shall come into force one year after the date of publication of
> this Gazette.
> (4) Rules 3, 5 to 16, 22 and 23 shall come into force eighteen months after
> the date of publication of this Gazette.

That is: the Board and definitions from **13 Nov 2025**, Consent Managers from
**13 Nov 2026**, and notice, security, breach, retention, rights, children and
the rest from **13 May 2027**. One secondary source says MeitY has floated a
proposal to shorten this to twelve months, which would move the deadline to
13 November 2026. That proposal is not notified, so the lawyer should confirm
the dates before relying on them. Building to the 2027 obligations now is
cheaper than retrofitting later.

What the Rules require that SheOut must build for (Gazette text):

- **Breach:** inform each affected person "without delay", and the Board
  "without delay" and in detail "within seventy-two hours of becoming aware".
  There is no runbook today. Phase 1: `docs/incident-response.md`.
- **Logs:** keep personal-data processing logs "for a minimum period of one
  year". Staff audit tables keep logs forever today. Set retention at no
  less than 1 year.
- **Rights requests:** answered "within a reasonable period not exceeding
  ninety days" (grievance). Self-service export and deletion already exist.
  Add a request route for correction, nomination and complaints.
- **Contact:** publish contact details of a person who can answer data
  questions (PIB: "a designated officer or a Data Protection Officer").
- **Children:** verifiable parental consent is required for under-18s.
  SheOut blocks under-18s instead, which is simpler. Keep that.
- **Third Schedule erasure** after inactivity applies to e-commerce, gaming
  and social-media fiduciaries above two crore registered users. SheOut is
  far below that. Inactive-account deletion is a choice, not an obligation
  (O-8).
- **Penalties** (PIB): up to ₹250 crore for failing to keep reasonable
  security safeguards, up to ₹200 crore for breach-notification or children's
  data failures, up to ₹50 crore for other breaches.

Roles (lawyer to confirm): SheOut, the brand of Snivika Technology Private
Limited, is the **Data Fiduciary**. Render, Vercel, AWS, Google
(Maps/Fonts/FCM), Anthropic and, later, an SMS provider and a BGV vendor are
**processors**. Razorpay is likely a fiduciary in its own right for payment
data. Data Processing Agreements are needed with each processor. Most
provide click-through DPAs; collect them (O-15).

**Cross-border:** DPDP s.16 allows transfers except to countries the
government restricts. None are listed today. Singapore and USA hosting is
allowed, but must be disclosed (issue 2).

### G-2. Ranked risks

1. **Misleading safety claims** (Consumer Protection Act 2019 s.2(28) and s.21,
   CCPA Misleading Advertisement Guidelines 2022). "Police-verified partners"
   while it is not true for every partner is the single most likely way to be
   sued after an incident. Fix before any public promotion. **Lawyer + you.**
2. **Privacy Policy that misstates third parties and data location** (DPDP
   notice, IT Act s.43A / SPDI Rules 2011 r.4, which require a published
   policy for sensitive data: biometrics, financial information). **Lawyer.**
3. **No grievance officer or entity details** (Consumer Protection (E-Commerce)
   Rules 2020 r.4(1)-(5): name, address, contact, grievance officer, 48-hour
   acknowledgement, one-month resolution; Razorpay activation needs the same
   on the website). **You + lawyer.**
4. **Motor Vehicle Aggregator Guidelines 2025 and Telangana:** a state licence
   is needed to operate as an aggregator. The secondary sources found say
   states adopt the guidelines and decide whether to allow non-transport
   (white-plate) two-wheelers; a Telangana drivers' union says white-plate bike
   taxis remain illegal there. No official Telangana notification was found.
   Also expected: police verification of drivers, a ₹5 lakh health and ₹10 lakh
   term cover for drivers, passenger insurance, SOS and a grievance channel.
   Today: no aggregator licence, no insurance, police verification in progress.
   **Lawyer: operating legality in Telangana is the first question, before any
   wording.**
5. **Biometric data** (SPDI Rules: biometrics are sensitive; DPDP: specific
   notice). Selfies and face-match scores are processed, but the consent text
   does not say face matching happens or how long selfies are kept. **Lawyer.**
6. **Women-only eligibility** (Article 15 applies to the State, not private
   firms; the Consumer Protection Act "unfair trade practice" and possible
   challenges). Wording of eligibility and how it is enforced. **Lawyer
   decides, not engineering.**
7. **Partner classification** (Code on Social Security 2020 "platform
   worker"; risk of creating employment). No Partner Terms exist. **Lawyer.**
8. **Payments and refunds:** wallet-only refunds (E-Commerce Rules r.5(3)
   expects refunds within a reasonable time; RBI PPI rules may apply if the
   wallet holds money that can be spent widely). Lawyer: is the SheOut wallet a
   closed-system PPI (exempt)? Razorpay needs a public refund policy, terms
   and contact page before live activation.
9. **SMS and DLT:** no promotional SMS may go out without DLT consent
   templates. Today nothing is sent, so there is no exposure, but SOS texts
   depend on it (`PENDING_DECISIONS.md` #25-26).
10. **Marketing push and email without opt-in** (DPDP consent per purpose;
    CAN-SPAM-style unsubscribe expectations for email). Add opt-in and an
    unsubscribe link.
11. **Security basics:** app CSP and headers missing (issue 14); token in
    localStorage; full bank numbers in the console (issue 12). OTP rate limits
    exist (`sharedkernel/ratelimit`). No secrets in the repo. Dependency
    audit: 4 high (Firebase SDK, issue 21). The Maven OWASP check
    (`D:\DevTools\dependency-check-data` exists) has not been run in this pass:
    Phase 1.
12. **Parcel liability and prohibited items:** the parcel rules exist and are
    versioned. Goods-in-transit cover does not exist, so no wording may imply
    cover. A liability cap is a lawyer item.
13. **App stores:** none today (a PWA). If a Play listing (TWA) is made:
    data-safety form, a public deletion URL (issue 18), permission reasons.
14. **Records:** consent records exist for Terms and verification. No
    marketing consent records, no access log for bank-number views, no
    incident plan.

---

## What I need from you before Phase 1

See `docs/legal-open-items.md`. The ones that block the most work:
O-1 (exact legal name), O-2 (address and CIN), O-4 (grievance officer),
O-8 (retention numbers), O-12 (refund rules), O-6 (claims wording), and the
asset sources (O-11).
