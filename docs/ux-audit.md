# UX audit: customer app and partner app

Branch `fix/ux-polish-pass`, cut from `origin/main` at 1c230f2 (same tree as
`feature/verification-checks`). Audit date 2026-10-10. Nothing changed yet.

## How this was checked

- **Screens:** 66 screens and states (39 customer, 27 partner),
  each at 375, 390 and 430 px in English, Hindi and Telugu, which is 594
  captures. Booking with location denied added 24 more, at 360 to 430 px.
- **Builds and data:** production builds of this branch were served locally.
  Every API call was answered from recorded real responses (the record/replay
  fixtures), so nothing reached any backend.
- **Error and offline states:** forced by failing the calls.
- **Automatic checks on every capture:** horizontal overflow, raw i18n keys,
  `undefined`/`NaN`/`null` text, "rider" in visible text, placeholder words,
  English-only text in Hindi/Telugu, touch targets under 44 px, unnamed
  buttons, images without alt text, page errors, and `alert`/`confirm` dialogs.
- **Code sweeps:** greps over both apps, the design system, locales, backend
  message strings, push copy, the help assistant knowledge base and the admin
  console.
- **Where the screenshots are:** outside the repo, in
  `D:\_qa-scratch\ux-audit\before\<screen>\<lang>-<width>.png`, with a `-full`
  variant at 390 px. The harness is `D:\_qa-scratch\ux-audit\audit.mjs`.
- **Map:** it shows "can't load" in every capture because the Maps key exists
  only on Vercel builds. This is expected and is not a finding.

## Clean results (evidence, no action)

| Check | Result |
|---|---|
| Horizontal overflow, 618 captures | 0 |
| Raw i18n keys / `undefined` / `NaN` / `[object Object]` visible | 0 |
| Page errors, `alert()` / `confirm()` dialogs | 0 |
| `alert(`, `confirm(`, `console.log` in frontend source | 0 |
| Lorem / TODO / TBD / `#` hrefs / empty `onClick` | 0 |
| Locale keys missing between en/hi/te (all 10 files) | 0 (5 values identical to English, all correct: "WhatsApp", "Google Maps", number templates) |
| Images without alt | 0 |
| Lunch Box in Home, menu or services | none; `/book/lunchbox` redirects to Home |
| Support address in the apps | only `support@sheoutride.com` |
| Double-tap guard on Book, Pay, Accept, Pickup code, Complete | present (`disabled` while in flight) |
| Delete account | present (Settings, typed confirmation) |

## Findings

Severity: **B** Blocker, **H** High, **M** Medium, **L** Low. **Q** means the
fix needs your decision; it is listed under Questions and will not be done
until you answer.

### Customer app

| # | Screen | Problem | Sev | File | Planned fix |
|---|---|---|---|---|---|
| C1 | Booking, Tracking, then Payment | Booking and tracking show **₹107**, but Payment asks for **₹107.44**. She is asked to pay more than any earlier screen showed. | H, **Q** | `QuoteSummary.tsx`, `TripPaymentCard.tsx`, `AmountText` | Decision needed (money): round fares to whole rupees on the server, or show paise everywhere. Tied to item 2.4. |
| C2 | Help assistant | The knowledge base says customers can book "bike taxi, auto, cab, parcel and **lunch-box** delivery". Only bike taxi and parcel exist, and Lunch Box is hidden for launch. | H | `backend/.../assistant/knowledge.md:12` | Say only bike taxi and parcel. |
| C3 | Any screen whose call fails | Technical text reaches her: "Request failed", "Request failed (500)", "Failed to fetch", "Unexpected token <". In Hindi and Telugu the English server text is added in brackets. 71 call sites show `err.message`. | H | `api/client.ts` `request()`, `lib/apiErrors.ts` (both apps) | One central fix. On a network failure, a non-JSON body or a 5xx, make the message a friendly line in her language ("Couldn't reach SheOut. Check your connection and try again."). Keep 4xx messages, which are written for people. |
| C4 | My Bookings, error state | A red "Request failed" line, with no explanation and no Try again. | H | `MyBookings.tsx:255` | Use the shared error card with Try again (as on the other screens since 1c230f2). |
| C5 | Searching / live trip | The phone's back button returns to the filled booking form, because booking **pushes** `/tracking/:id`. | H | `RideBooking.tsx:110`, `DeliveryBooking.tsx:183` | `navigate(..., { replace: true })`, so back goes to Home. |
| C6 | Book ride / parcel sheet | Only a **24 px strip** above the content drags the sheet. A swipe on the sheet body (where a thumb lands) does nothing. The strip is also under the 44 px target. See 2.2. | H | `BookingMapLayout.tsx` | Drag from the whole collapsed sheet. When expanded, scroll the content and drag the sheet only once the content is at the top. Handle area 44 px. Book button pinned below the scroll area. Playwright touch test, 20/20 each way, Chromium and WebKit. |
| C7 | Book sheet, expanded | Expanding shows **the same fare and km again** (`FareEstimateCard`) and a "fixed price" paragraph that repeats the "Fixed price" label. See 2.3. | H | `RideBooking.tsx` `more`, `DeliveryBooking.tsx` | Expanded shows only new information: nearby partners, the promo breakdown when one applies, payment method and safety. The fare stays once, on the option card. |
| C8 | Home and every screen, offline | No offline banner. Home shows "Hello, there" with nothing loaded and no reason given. | H | (none exists) | Small shared `OfflineBanner` (design system) driven by the `online`/`offline` events, shown in both apps. |
| C9 | Home, About, Login, errors, Safety Center | "Women **riders**", "No **rider** account on this number", "Every SheOut Seller is a verified SheOut **rider**", "Every **rider** sends an ID…". 9 customer strings in English (10 in Hindi), across all 3 languages. | H | `i18n/{en,hi,te}*.json` | "customer" where she must be named, otherwise "you". The full list will be in the final report. |
| C10 | Customer error `INVALID_PICKUP_CODE` | Tells the *customer* "Ask your **rider** to read it out again", but she is the one holding the code. | H | `customer-app/src/i18n/*.json:933` | Correct who does what: "That code doesn't match. Check the code on your screen and read it out again." |
| C11 | Book ride / parcel, location off | "Tap to choose your pickup point" wraps to 2 lines (te 360/375, hi 360). See 2.1. | M | `BookingMapLayout.tsx` `LocationChip`, locales | "Choose pickup point" (plus short hi/te). Placeholder on one line with ellipsis. **Real addresses keep 2 lines**: a code comment records that one line cut most Hyderabad addresses mid-locality. |
| C12 | Tracking | "Updated **418530s** ago": seconds are never turned into minutes or hours. A partner with no signal for 5 minutes would show "Updated 300s ago". | M | `Tracking.tsx:661, 1050, 1220` | "Updated just now / 2 min ago / over an hour ago". |
| C13 | Tracking, partner assigned | The Fare row has no inner padding; "Fare" touches the card edge. | M | `Tracking.tsx` | Same padding as the card above it. |
| C14 | Tracking, partner assigned | Chat appears twice: an icon on the partner card and a Chat button under it. | M | `Tracking.tsx:714, 825` | Keep the Share trip and Chat row; drop the icon on the card. |
| C15 | Tracking, searching | "Finding a partner" appears 3 times (title, heading, badge). The fare is labelled "Estimated Fare" here but "Fixed price" one screen earlier. Chat is offered before any partner exists. | M | `Tracking.tsx` | Drop the badge. One fare label, matching the booking screen. Hide Chat until a partner is assigned. |
| C16 | Wallet, Payment, Promo lines | ₹ without Indian grouping: "Balance ₹1537.58" under a header "₹1,537.58"; promo discount `toFixed(2)`; the struck-out fare. | M | `Wallet.tsx`, `PromoFareLines.tsx`, `QuoteSummary.tsx:81`, `PaymentSuccessFlash.tsx` | Use the shared `AmountText` formatter everywhere. |
| C17 | Home | The floating Help assistant bubble ("Hi there! I am here…") and the raised SOS button cover the Marketplace card title. | M | `Home.tsx`, assistant launcher | Add bottom space under the last card so nothing sits under the floating buttons. |
| C18 | Menu, Home | One feature, three names: "Marketplace", "Sell on SheOut", "SheOut Seller". | M | locales | "SheOut Seller" (menu and Home card), "Sell on SheOut Seller" for the shop entry. |
| C19 | Settings | "Rate the App" opens only a "once SheOut is in the app stores" toast, so it is a dead row. | L | `Settings.tsx:101` | Hide the row while there is no store URL. |
| C20 | Menu, Settings | Capitalisation mixes Title Case and sentence case ("Refer a Friend", "Safety Center", "Log Out" next to "Help & support"). | L | locales | Sentence case throughout, in English only. |
| C21 | Cancel ride | Cancelling is always free (the backend has no cancellation fee), but she is not told so. | L | `Tracking.tsx` cancel step | One line: "No charge for cancelling." |
| C22 | Home | One icon-only button has no accessible name (every language). | L | `Home.tsx` | Add `aria-label`. |

### Partner app

| # | Screen | Problem | Sev | File | Planned fix |
|---|---|---|---|---|---|
| P1 | Pickup check, Trip, Payment, Home, Help, Verification, Chat, About | "rider" throughout: "Is this your **rider**?", "Show UPI QR to **rider**", "Waiting for ₹107 from your last **rider**", "**Riders** never see your documents". 47 strings × 3 languages, plus 3 safety strings. | H | `driver-app/src/i18n/*.json` | "customer" or "your customer". Safety strings go to the native-review list. |
| P2 | Home, network down | A raw "Request failed" line. | H | same as C3 | Same central fix. |
| P3 | New request (offer) | Shows the **fare (₹107)**, not her earnings. The offer payload has only `fareEstimate`. | H, **Q** | `DispatchController.OfferResponse`, `Offer.tsx` | Needs a server field (her net for this trip). A small backend addition, but it touches money display, so I am asking first. |
| P4 | New request | Shows **5.9 km**, while the customer was quoted **7.9 km** for the same trip. | M | `Offer.tsx` | Investigate: the offer probably shows straight-line distance. Show the routed distance from the booking. |
| P5 | Trip, Payment card | `₹{booking.finalFare}` and `toFixed(2)` without grouping (₹1250.00). | M | `Trip.tsx:857, 1081`, `CollectPaymentCard.tsx:225` | Shared formatter. |
| P6 | Bottom nav | "Home · Earnings · **Bookings** · Profile". The brief wants "Home · **Trips** · Earnings · Profile". | M | `driver-app/src/layout/AppShell.tsx`, locales | Rename the label to Trips and reorder. Route names stay unchanged. |
| P7 | Trip complete | ₹107.44 appears twice (the payment header and the Fare row). | L | `Trip.tsx` | Keep it in the payment header; drop the duplicate row while payment is pending. |
| P8 | Payouts | Account shown as "XXXXXXXX9012", which reads like a template. | L | `Payouts.tsx` | "•••• 9012". |

### Backend copy, admin console and legal

| # | Where | Problem | Sev | File | Planned fix |
|---|---|---|---|---|---|
| S1 | Push notifications | "Your rider wants to change the destination", "The rider cancelled this trip", "Riders now see…", "Your riders miss you". 58 lines across en/hi/te. | H | `NotificationCopy.java` | "customer". |
| S2 | API error messages shown in the apps | About 20 user-facing literals ("Ask your rider…", "Only a rider has a SheOut wallet", "Google sign-in is only for the rider app"). | M | `*Controller.java` | Wording only; error codes unchanged. |
| S3 | Help assistant | 11 "rider" mentions; it also *calls the customer* "rider" in its system prompt. | M | `knowledge.md`, `KnowledgeBase.java:77-80` | "customer" and "customer app". |
| S4 | Admin console | 121 visible "Rider" labels ("Rider ID checks", "Rider promotions", "Trips per rider"). | M | `static/admin/index.html` | "Customer". CSP hashes are computed at startup, so nothing else changes. LF line endings kept. |
| S5 | Terms and Privacy | Say "riders" and "driver-partners", and are English-only in the Hindi and Telugu apps. | **Q** | `design-system/src/legal/content.ts` | Legal wording: will not be edited without you or your lawyer. |
| S6 | Settings, Grievance Officer | "Contact details are being set up". The IT Rules require a named officer. | **Q** | `Settings.tsx` | Needs the officer's details from you. |
| S7 | Emergency contacts, SOS copy | Promises "everyone here is sent an SMS". SMS has no provider yet (PENDING_DECISIONS #25–29). | note | `en.safety.json` | No change. The copy is true once SMS is live; it stays tracked as a launch blocker. |
| S8 | DB-seeded text (content blocks, campaigns) | Some seeded rows may say "rider". | **Q** | `V17`, `V36`, `V45` migrations | Changing them needs a new Flyway data migration. Do you want one? |

## Phase 1 structure: differences from the brief (decisions, not fixes)

The current menu and Profile are the layout you chose on 2026-10-06 (commit
`ede09f7`: "nothing in both the menu and Profile"). The brief's Phase 1
layout differs:

- **Customer bottom nav has 5 items:** Home, Bookings, **SOS (raised)**,
  Wallet, Profile. The brief says 4. Removing SOS from the nav is a safety
  change: **Q1**.
- **Customer menu, now:** Safety Center, Refer a Friend, Marketplace, Sell on
  SheOut, Help & support, Settings. Notifications live on the Home bell;
  Language, About, Terms, Privacy, Log out and Delete account are in Settings.
- **Customer Profile, now:** Personal details, Saved addresses, Payment
  history, ID verification, Emergency contacts.
- **Brief vs today:** the brief puts Language, Notifications, Referral, Help,
  Terms, Log out and Delete account in Profile *and* several in the menu,
  which reverses ede09f7: **Q2**.
- **Partner Profile:** lacks rating, Language, Help, Log out and Delete
  account. They are in the menu's Settings: **Q2**.

## Questions that block a fix

1. **Q1, SOS in the bottom nav.** Keep it raised in the middle (5 items),
   or move it to a floating button and keep 4 tabs?
2. **Q2, menu and Profile.** Keep the ede09f7 layout (no duplicates), or
   rebuild to the brief's lists?
3. **C1, paise.** Should fares be whole rupees on the server (charge ₹107),
   or should every screen show ₹107.44?
4. **P3, offer earnings.** May I add her net earnings for the trip to the
   offer response, so the offer shows net first and the fare below?
5. **Item 2.4, fare range.** Stopped before any code. What I found:
   - There is **no pre-trip hold or authorisation**. She pays after the trip,
     from her wallet or Razorpay. "Hold the upper figure, capture the real
     fare" would be a **new payment flow**, not a display change.
   - Pricing is deterministic. The fare charged *is* the quote, and it
     changes only through the existing agreed destination-change flow. A
     range of ₹102–112 would show less than the system knows. Today's copy
     promises the opposite ("The price you see is the price you pay"), and
     code comments record that this was chosen on purpose.
   - **Places the range would touch:** `QuoteSummary`, `FareEstimateCard`,
     `booking.fixedPrice`, `booking.fixedPriceNote` and the Help FAQ.
     Receipts, refunds, referral credit and the GST/platform-fee lines all use
     the final fare and **would not change**. The promo "you pay" line would
     need its own range.
   - Shall I go ahead with display only (no hold), or leave fares exact?
6. **S5, S6, S8.** Legal text, the Grievance Officer, and the seeded DB text.
7. **Native reader.** The new hi/te wording for "customer" will be listed for
   review. In safety copy, the English stays beside it until it is reviewed
   (the existing rule).

## Proposed fix order (once you reply)

1. **High, no decision needed:** C2–C10 and P1, P2, S1. Order: rider sweep,
   error text, back button, bottom sheet, repeated fare, offline banner.
2. **Medium:** C11–C18, P4–P6, S2–S4.
3. **Low:** C19–C22, P7, P8.
4. **Q items:** after your answers.

## S8: seeded "rider" text in the database (proposed V79, NOT created, NOT run)

The apps read `content_blocks` for About, Home and the Help FAQ, **in English
only** (Hindi and Telugu use the locale files). When a row has a value, it
overrides the locale string, so these rows are what English users see in prod
unless staff have edited them. Column comments in V9, V12 and V14 also say
"rider", but they are database documentation that nobody sees in the apps.

| content_key | Seeded value (exact) | Set by | Proposed value |
|---|---|---|---|
| `about.body` | Rides and deliveries built around women's safety - women riders, women drivers. | V17 | Rides and deliveries built around women's safety: every customer and every partner is a woman. |
| `faq.driver.2.answer` | Riders see it when you are on your way. It is how a woman getting into a stranger's vehicle at night checks she has the right one. It is never shown to anyone before you accept a trip. | V17 | Customers see it when… (rest unchanged) |
| `faq.driver.3.answer` | …Requests also pause for up to ten minutes after a trip while the rider pays. | V25 | …while the customer pays. |
| `faq.driver.4.question` | How do I contact my rider? | V17 | How do I contact my customer? |
| `faq.driver.6.answer` | Your earnings are your share of every trip your riders have paid for. … | V25 | …every trip your customers have paid for. … |
| `description` of 21 rows | "Rider app, Home: …", "Rider app, Help & Support: …" (staff see these in the console's content editor) | V17 | "Customer app, …" |

Proposed `V79__customer_wording_in_content.sql`. V78 is taken by
`next/drop-rc-document-key-v78`. Every value UPDATE matches the exact seeded
text, so a row an operator has edited is left alone:

```sql
UPDATE content_blocks SET value = 'Rides and deliveries built around women''s safety: every customer and every partner is a woman.', updated_at = now()
 WHERE content_key = 'about.body' AND value = 'Rides and deliveries built around women''s safety - women riders, women drivers.';
UPDATE content_blocks SET value = 'Customers see it when you are on your way. It is how a woman getting into a stranger''s vehicle at night checks she has the right one. It is never shown to anyone before you accept a trip.', updated_at = now()
 WHERE content_key = 'faq.driver.2.answer' AND value = 'Riders see it when you are on your way. It is how a woman getting into a stranger''s vehicle at night checks she has the right one. It is never shown to anyone before you accept a trip.';
UPDATE content_blocks SET value = 'You must be online, verified, and allowing location access - requests are offered to the nearest available partners first, then further out. Requests also pause for up to ten minutes after a trip while the customer pays.', updated_at = now()
 WHERE content_key = 'faq.driver.3.answer' AND value = 'You must be online, verified, and allowing location access - requests are offered to the nearest available partners first, then further out. Requests also pause for up to ten minutes after a trip while the rider pays.';
UPDATE content_blocks SET value = 'How do I contact my customer?', updated_at = now()
 WHERE content_key = 'faq.driver.4.question' AND value = 'How do I contact my rider?';
UPDATE content_blocks SET value = 'Your earnings are your share of every trip your customers have paid for. Each paid fare is added to your SheOut wallet, and you can request a payout to your bank or UPI from Wallet & payouts.', updated_at = now()
 WHERE content_key = 'faq.driver.6.answer' AND value = 'Your earnings are your share of every trip your riders have paid for. Each paid fare is added to your SheOut wallet, and you can request a payout to your bank or UPI from Wallet & payouts.';
-- Staff-facing labels in the console's content editor; never shown in the apps.
UPDATE content_blocks SET description = 'Customer app,' || substr(description, length('Rider app,') + 1)
 WHERE description LIKE 'Rider app,%';
```

**Before running it:** check prod with
`SELECT content_key, value, updated_by FROM content_blocks WHERE value ILIKE '%rider%';`.
A row with `updated_by` set was edited by staff and will not match, so it
keeps its text and needs a console edit instead.

## Results (2026-10-10)

Fixed on `fix/ux-polish-pass`. Nothing pushed or deployed. Composites are in
`docs/ux-after/<screen>-<lang>-<width>.jpg`, before on the left and after on
the right.

### Automatic checks, all 618 captures (66 screens at 375/390/430 px in en/hi/te, plus location-off states)

| Check | Before | After |
|---|---|---|
| Visible "rider" text | 353 | 0 |
| Touch targets under 44 px | 1,245 | 905 |
| Buttons with no accessible name | 8 | 1 (transient, during the menu animation; not reproducible) |
| Overflow / raw keys / `undefined`/`NaN` / page errors | 0 | 0 |

### Findings and fixes

| Finding | Screen | Fix | File | Evidence |
|---|---|---|---|---|
| C1 ₹107 shown, ₹107.44 charged | Booking → Payment | Fare rounded once to whole rupees at the quote; one formatter for every ₹ | `DistanceBasedFareCalculator`, `AmountText` | 4 new unit tests; e2e: quote 77 = booking 77 = final 77 = paid 77.00 |
| C2 Assistant offers auto/cab/lunch box | Help assistant | Knowledge base: bike taxi and parcel only; seller monthly plan | `assistant/knowledge.md` | grep: 0 lunch/auto/cab |
| C3/P2 Technical error text | Any failing call | `send()`/`parseJson()` and `requestErrorMessage()`: friendly text in her language for network/5xx/non-JSON; no English in brackets | both `api/client.ts`, `apiErrors.ts`, `lib/requestErrors.ts` | `c-bookings-error-en-390.jpg` |
| C4 My Bookings error has no retry | 6 lists | Shared `LoadFailed` card with Try again | `MyBookings`, `PaymentMethods`, `Wallet`, `Seller`, partner `Bookings`, `SupportTicketList` | `c-bookings-error-en-390.jpg` |
| C5 Back returns to the sent form | Searching | `navigate(..., { replace: true })` | `RideBooking`, `DeliveryBooking` | sheet test: back → `/home`, Chromium and WebKit, 3 widths (old build: `/book/ride`) |
| C6 Sheet drags only on a 24 px strip | Book ride / parcel | Whole-sheet drag; details scroll; pull-down at their top collapses; 44 px handle; Book never scrolls away | `BookingMapLayout` | Chromium 27/27 (20/20 each way × 3 widths); WebKit 20/20 each way; old build 0/20 |
| C7 Expanded sheet repeats fare and km | Book ride / parcel | `TripSheetDetails`: promo breakdown, how she pays, what fixed price means, partners nearby | `TripSheetDetails.tsx` | `sheet-expanded-chromium-4183.png`; test "details repeat no fare/km" |
| C8 No offline banner | Every screen | `OfflineBanner` pill, clear of the corner buttons | `OfflineBanner.tsx`, both `App.tsx` | `c-home-offline-en-390.jpg` |
| C9/C10/P1/S1–S4 "rider" | Both apps, push, errors, assistant, console, Terms, Privacy | "customer" (कस्टमर / కస్టమర్) | locales, `NotificationCopy`, controllers, `knowledge.md`, `index.html`, `legal/content.ts` | 186 locale values → 0; 353 visible → 0 |
| C11 Pickup prompt wraps | Book, location off | "Choose pickup point", one line with ellipsis; addresses keep 2 lines | `BookingMapLayout`, locales | `c-book-ride-nogeo-te-360.jpg` |
| C12 "Updated 418530s ago" | Tracking | just now / Ns / N min / over an hour | `Tracking.tsx` | `c-assigned-en-390.jpg` |
| C13 Fare row has no padding | Tracking | `px-5 py-3` | `Tracking.tsx` | `c-assigned-en-390.jpg` |
| C14 Chat twice | Tracking | Chat on the partner card only | `Tracking.tsx` | `c-assigned-en-390.jpg` |
| C15 "Finding a partner" ×3, "Estimated Fare", Chat with nobody | Searching | Badge removed, label "Fare", Chat waits for a partner | `Tracking.tsx` | `c-searching-en-390.jpg` |
| C16/P5 ₹ without grouping | Wallet, payment, partner trip | `formatAmount`/`formatRupees` | 9 files | `c-wallet-en-390.jpg` |
| C18 Three names for SheOut Seller | Home, menu | "SheOut Seller" | locales | `c-menu-en-390.jpg` |
| C19 Dead "Rate the app" | Settings | Shown only with a store link | `Settings.tsx` | `c-settings-en-390.jpg` |
| C20 Mixed capitalisation | Menu | Sentence case (English) | locales | `c-menu-en-390.jpg` |
| C21 Cancel fee not stated | Cancel dialog | "Cancelling is free." (no fee exists in the backend) | `Tracking.tsx` | locales |
| C22 Unnamed menu header | Menu | `aria-label` while loading | `AppDrawer.tsx` | unnamed 8 → 1 |
| P3 Offer shows the fare, not her pay | New request | `partnerEarnings` + `commissionPercent` (payments' `PlatformCommission`) | `DispatchController`, `PaymentApi`, `Offer.tsx` | `OfferEarningsTest`; e2e ₹63.14 of ₹77; `d-offer-en-390.jpg` |
| P4 5.9 km vs quoted 7.9 km | New request | Routed distance; straight line only as fallback | `Offer.tsx` | `d-offer-te-390.jpg` |
| P6 Tabs | Partner nav | Home · Trips · Earnings · Profile | `AppShell.tsx`, locales | `d-trips-en-390.jpg` |
| P7 Fare twice | Partner trip complete | Shown once, in the payment card | `Trip.tsx` | `d-trip-complete-en-390.jpg` |
| P8 "XXXXXXXX9012" | Payouts | "•••• 9012" | `Payouts.tsx` | `d-payouts-en-390.jpg` |
| Profile additions | Both Profiles | Delete my account (same two-step flow), app version | `Profile.tsx` ×2, `PrivacyDataSection` | `c-profile-en-390.jpg`, `d-profile-en-390.jpg` |
| Touch targets | Header, chips, login | 44 px minimums in CSS px | `TopHeader`, `BookingMapLayout`, `Login` ×2 | 1,245 → 905 |

### Verification

- **Backend:** 546 tests, 0 failures, 0 errors (`mvn clean test`, a fresh
  database, UTC, Redis 8 on 6380).
- **Frontend:** both apps pass `tsc -b` and `vite build`. Neither app has a
  test or lint script.
- **End-to-end** (local backend, dev mode, a scratch copy of the local
  database), 16/16 checks: book, offer with earnings, accept, pickup code,
  start, drop, complete, wallet payment. Quote ₹77 = booking = final = due =
  paid ₹77.00; the wallet fell by exactly ₹77.00.

### Not fixed, and why

- **Bottom nav has 5 items:** SOS stays raised, as you decided.
- **SOS sends on one tap of the circle**, with no confirm and no hold.
  Reported, not changed (your decision).
- **Fare range (2.4):** dropped, as you decided.
- **Percentage promotions** can still leave paise in what she pays
  (10% of ₹107 = ₹10.70). Needs your decision.
- **Partner verification consent** (`legal/content.ts`): still says "carry
  riders" and "Riders never see my documents". It's legal text, so not
  touched.
- **`LEGAL_VERSION` not bumped** after the Terms/Privacy wording change, to
  avoid a re-accept prompt.
- **Seeded content rows:** V79 is proposed above, not created.
- **905 small touch targets remain.** The root cause is `theme.css` scaling
  the root font below 390 px, so every rem-sized target shrinks on small
  phones. Fixing it globally changes every layout, so it's a separate pass.
- **Not cleaned up:** hardcoded hex colours in illustration components
  (MoodFace, ThreeWomen, the login art) and 7 one-off `text-[Npx]` sizes.
  These are artwork and tiny chips, left as they are.
- **The assistant's greeting bubble** briefly covers the SheOut Seller card
  title on first view. Low priority.
- **The pull-down-on-details gesture is untested on WebKit:** desktop WebKit
  cannot synthesise touch events, so it needs a real iPhone check.
