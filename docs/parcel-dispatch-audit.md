# Parcel proof-of-delivery and dispatch: audit (Phase 0)

Branch `feat/parcel-proof-and-dispatch`, cut from `origin/main` at `f33f938`.
`fix/ux-polish-pass` is already in main: it was rebased, so the hashes differ,
but `origin/fix/ux-polish-pass` and `c4a87b3` on main have identical trees.

Backend paths are relative to `backend/src/main/java/com/sheout/`. Frontend
paths are relative to `frontend/`. Line numbers are at commit `87b4399`.

Evidence from real runs:
- `backend/src/test/java/com/sheout/dispatch/internal/BusyPartnerGetsNoOfferTest.java`
  runs against real Postgres and real Redis; 6/6 pass.
- 168 trip-screen captures before and 168 after (bike and parcel, 7 states,
  360/375/390/430 px, en/hi/te), with measured positions and sizes.
  Pairs are in `docs/ux-after-trip/`.
- Customer e2e tests: `frontend/customer-app/e2e/trip-actions.spec.ts`
  (7/7) and `sos-countdown.spec.ts` (3/3).

---

## A. Parcel delivery today

A parcel is a ride with a different category. It uses the same booking
record, the same state machine, the same pickup code and the same completion
rule. Nothing about a delivery is checked at the drop.

### A.1 States, and what the partner sees and does

`REQUESTED -> MATCHED -> ACCEPTED -> IN_PROGRESS -> COMPLETED`. A booking can
also end as `CANCELLED` or `NO_DRIVERS_AVAILABLE`
(`booking/internal/BookingStateMachine.java:40-49`).

| State | Partner sees and does | Evidence |
|---|---|---|
| REQUESTED | Offer screen with pickup, drop, fare and her earnings, with a chime; she accepts or declines within 15 s | `dispatch/internal/DispatchService.java:427-478`, `driver-app/src/screens/Home.tsx:235-247` |
| MATCHED | She has claimed it and confirms | `booking/internal/BookingService.java:353` |
| ACCEPTED | Navigates to the pickup. The 4-digit pickup code is generated now | `BookingService.java:402` |
| At pickup | Enters the sender's code. Allowed only within 150 m of the pickup, 5 attempts | `BookingService.java:435-498`, `booking/internal/PickupCode.java:44` |
| IN_PROGRESS | Navigates to the drop. Parcel note shown; hint "Hand over the parcel, then end the trip." | `driver-app/src/screens/Trip.tsx:708-721` |
| COMPLETED | Taps End trip. Away from the drop she picks a reason, and the trip still ends | `BookingService.java:555-599` |

### A.2 Codes
- **Pickup code: yes.** It goes to the sender (the customer who booked), in
  the app only, and only once the partner is within the pickup radius
  (`BookingService.java:511-521`).
- **Delivery code: no.** Nothing at the drop proves the parcel reached anyone.
- **Receiver: not modelled.** The booking has no receiver name or phone
  (`booking/internal/BookingEntity.java:233-245`: only `parcel_note`,
  `order_product_id` and `order_quantity`). The parcel form asks only for an
  optional note, and phone numbers in it are refused (`BookingService.java:302-305`).
- **SMS: none.** SMS to Indian numbers is blocked until DLT approval, so any
  receiver code has to go through the sender's app for now: she reads it
  out, or forwards it herself.

### A.3 Photos
**None** at pickup or delivery. Photo infrastructure already exists and can be
reused: private storage in Postgres (`sharedkernel/storage/DatabaseDocumentStorage.java`)
behind signed links valid for 1–2 hours (`sharedkernel/storage/DocumentLinkSigner.java:33`),
with a 30-day photo retention sweep for shift checks
(`driververification/internal/ShiftCheckService.java:68`).

### A.4 Distance check at the drop
There is a 150 m geofence (`BookingService.java:123`), but it is **not a gate**.
Outside it, or with a stale GPS fix, the partner picks a reason and the trip
still completes (`BookingService.java:579-586`). The choices are
`CUSTOMER_REQUESTED_DIFFERENT_DROP`, `ROAD_CLOSED_OR_BLOCKED` and `OTHER` with
a note. The distance and reason are recorded, and the route check flags the
trip for review.

### A.5 Receiver unreachable or refusing; sender cancels after pickup
- **Receiver absent: no flow.** From IN_PROGRESS the only allowed transition
  is COMPLETED (`BookingStateMachine.java:44`). A partner who can't hand it
  over can only end the trip, with a reason such as `OTHER`.
- **Sender cancels after pickup: impossible.** CANCELLED isn't reachable from
  IN_PROGRESS. What the sender can do is **"End here"**
  (`BookingService.java:613`, `customer-app/src/screens/Tracking.tsx:214`).
  For a parcel this marks it *delivered* and charges her, though she isn't at
  the drop. **Finding:** this is a mis-tap risk, and a dispute risk because
  the sender has closed the delivery herself. I left it as is: it touches when
  she is charged. **Decision D7.**

### A.6 Payment for parcels
- **Charged after delivery**, at COMPLETED. `BookingCompleted` creates the
  payment and the sender pays in the app (`payments/internal/BookingCompletedListener.java:56`).
- **Who pays:** the sender. There is no receiver-pays option.
- **Cash: refused** (HTTP 410, `payments/internal/web/PaymentController.java:155`).
- **Unpaid:** the sender can't book again (`BookingService.java:277`), and the
  partner gets no new offer until it's paid or 10 minutes pass
  (`BookingService.java:121`, `dispatch/internal/DispatchService.java:519`).
- **Partner payout:** her share goes into her wallet at capture
  (`payouts/internal/PayoutService.java:91`). She can request payout at once
  (`PayoutService.java:171`), and an operator pays manually and records it
  (`PayoutService.java:212`). **No per-trip hold exists**; an admin can only
  delay paying or block the account.

*Consequence for theft:* the sender pays after delivery, so a partner who
walks off with a parcel costs the sender the parcel, not the fare. But a
false "Delivered" today makes the sender pay, credits the partner, and needs
a manual refund (`admin/internal/web/AdminRefundController.java`).

### A.7 Declared value, size and weight, prohibited items
**None.** The parcel screen has an optional note only
(`customer-app/src/screens/DeliveryBooking.tsx:263-269`). Bike-only matching
is hard-coded (`DispatchService.java:624`).

### A.8 What an admin sees for a disputed parcel
`OpsViews.BookingDetail` (`admin/internal/OpsViews.java:121-135`) shows the
booking, the ops facts (pickup verified at, pickup attempts, completion
distance from the drop, deviation reason and note, quoted and actual
distance, route flag), the payment, the GPS trail, SOS alerts, ratings, trip
alerts and the event log. **Missing:** a delivery code, photos, a geofence
result shown as such, and one-click "hold earnings" or "open dispute".

### A.9 Copy that is wrong today (not fixed: outside the trip screens)
The parcel form says "she can call you through the app" (en/hi/te
`delivery.parcel.detailsHint`). No in-app calling exists; it's chat only
(`Tracking.tsx` file comment). **Decision D8:** reword it to "she can message
you in the app".

---

## B. Dispatch today

### B.1 Who can receive an offer
The single gate is `isAvailableNow` (`dispatch/internal/DispatchService.java:506-542`):
online, not blocked, **no payment hold** (`:519`), **no active trip** (`:525`),
test partners only for test riders, a valid shift selfie, and currently
verified. On top of that:
- a fresh location (< 2 min, `:209`, `:440-442`);
- the right vehicle (`:619-626`);
- not the customer herself;
- no offer already on her screen (`:449`).

"Active trip" means MATCHED, ACCEPTED or IN_PROGRESS
(`booking/internal/BookingService.java:58`), read from Postgres each time
(`booking/internal/BookingRepository.java:70`).

**A partner on a trip in any phase gets no new offer.** The integration test
proves it against real Postgres and Redis:

| Her current trip | Offered the next one? |
|---|---|
| none | yes |
| MATCHED | no |
| ACCEPTED (heading to pickup or waiting at it) | no |
| IN_PROGRESS | no |
| COMPLETED, unpaid, inside the 10-minute hold | no |
| COMPLETED and paid | yes |

The same gate runs again at accept (`DispatchService.java:301-311`).

### B.2 How the search works
| Setting | Value | Evidence |
|---|---|---|
| Initial radius | 3 km | `DispatchService.java:92`, `application.yml:399` |
| Expansion | ×2 per round: 3, 6, 12, 24 km | `DispatchService.java:380` |
| Rounds | 1 + 3 retries = 4 | `DispatchService.java:375` |
| Offer window | 15 s | `application.yml:402` |
| Offered at once | up to 5 nearest eligible partners (first to accept wins) | `DispatchService.java:452-454` |
| Total budget | 90 s, an absolute deadline; no round starts unless it can finish in time | `DispatchService.java:367-371` |
| Sweep | every 2 s, one server at a time | `dispatch/internal/DispatchRetrySweeper.java` |
| Ranking | nearest by straight-line distance (GEOSEARCH) | `dispatch/internal/matching/NearestFirstMatchingStrategy.java` |

**When nobody accepts:** the booking becomes `NO_DRIVERS_AVAILABLE`
(`booking/internal/DispatchExhaustedListener.java:44`). The customer sees
"No partners available" with **Try again** (a fresh booking) and **Cancel**,
and nothing is held against her. Copy changes at 0, 15, 35 and 60 s and
matches what the search is actually doing (`Tracking.tsx:77`).

**Radius expansion vs H3:** GEOSEARCH with expansion is enough at this
scale. Nothing in the audit justifies H3.

### B.3 How busy and available are stored; races
- **Online status:** users module (Postgres). **Location:** Redis geo set.
  **Busy:** derived from `bookings` on every check; there is no stored flag
  to drift out of sync.
- **Offer slot:** one per partner, `SET NX` with the window as TTL
  (`dispatch/internal/redis/OfferStore.java:52`). Two searches can't both
  give her an offer.
- **Booking claim:** `SET NX` per booking (`OfferStore.java:99`). Two
  partners can't both win a booking, and the losing accept is refused. Then
  `assignDriver` locks the booking row (`BookingService.java:353-375`).
- **Gap: nothing in the database enforces "one active trip per partner."**
  `assignDriver` doesn't check her other bookings. Today it holds because a
  partner can hold only one offer at a time, and accepting re-checks
  availability. One small window exists: between accepting trip A
  (`removeOffer`) and the round filter for trip B, an offer for B can reach
  her screen. Accepting it is refused, because `isAvailableNow` sees trip A,
  and her app doesn't poll while she has a trip. Queued offers would create
  a deliberate second hold, so **Part 2 must add a database constraint**
  (see the design doc).

### B.4 Partner app with a new offer during a trip
Never happens today. The app polls for offers only while she is online with
no active trip (`driver-app/src/screens/Home.tsx:235`), and the server
doesn't offer anyway (B.1). An offer when she is free is **full-screen**
(navigates to `/offer/{id}`) with a chime and vibration
(`driver-app/src/lib/offerChime.ts`), plus a push with ALERT urgency
(`notifications/internal/NotificationEventListeners.java:119-130`). That
full-screen pattern must not be reused mid-trip.

### B.5 Metrics
**No dispatch metrics exist.** There is no Micrometer meter in the code
(Actuator is used only for health).
- **Logs:** "Dispatch gave up on booking … after N rounds and Ns"
  (`DispatchService.java:401`) and the NO_DRIVERS_AVAILABLE log line.
- **Can be derived from SQL:** the no-partner-found rate (status
  NO_DRIVERS_AVAILABLE), time to match (`matched_at - created_at`), and
  cancellation after match (`cancelled_at` with `matched_at` set). The
  `booking_events` table records each transition.
- **Can't be derived:** acceptance rate and offers per partner per hour.
  Offers live only in Redis and expire. Part 2 adds an `offer_log` table.

---

## C. Trip screens (bike taxi and parcel) — fixed in Part 3

Measured on the captures, before → after. The harness runs the real customer
app against recorded API answers; a parcel is the recorded trip with
type DELIVERY.

| # | Problem before | After |
|---|---|---|
| C1 | Cancel was a 44 px pill in a bar pinned to the bottom, next to the gesture bar. It read as an afterthought and was easy to hit while reaching for Share | One action row on the card (Chat, Share, Support, Cancel), icons over labels, **56 px** tall. Cancel is last and outlined, not filled |
| C2 | At the kerb (partner arrived, not yet confirmed) Cancel was below the fold at every width (top 746 on a 640 screen) | The check comes first, with both answers in view at every width and language; the row follows (top 586–667). "Something's not right" is itself a no-charge cancel |
| C3 | Cancel was 44 px, below the 48 px button target | ≥ 56 px |
| C4 | A parcel said "Cancel Ride" | "Cancel delivery" as the accessible name; "Cancel" on the button |
| C5 | A parcel sender saw "Check before you get on" and "Don't get on" | "Check before you hand it over" and "Don't hand it over"; the ride-only safety line is hidden |
| C6 | A parcel's on-trip title said "On the way to your drop" and "On your trip" | "Your parcel is on its way" and "Parcel on its way"; the near-drop hint is parcel wording |
| C7 | A parcel sender was offered "I found another ride" | Removed from the parcel reason list |
| C8 | A parcel's contents never appeared on the sender's screen | A "Your parcel" card with what's inside and what to do at this step |
| C9 | The partner card had a lone chat circle and a separate full-width Support button, with the actions spread over three places | One row in one place, the same for bike taxi and parcel |

**Cancel rules (checked, they match):** the customer can cancel in REQUESTED,
MATCHED and ACCEPTED (`Tracking.tsx` `canCancel`), and the server allows
CANCELLED from exactly those (`BookingStateMachine.java:40-43`). Once the
trip starts there's no Cancel, only Chat, Share and Support with SOS on the
map; both e2e and the captures check this. **"Cancelling is free" is
true:** no cancellation fee exists anywhere in the backend.

**Checks after (168 captures):** Cancel is in view and ≥ 48 px wherever it's
offered and absent after the trip starts. No target is under 44 px, there's
no horizontal overflow, no page errors and no "rider" in the copy. The
booking sheet still passes its touch tests at 20/20 each way (Chromium
27/27; WebKit the same as when it shipped, where the details pull-down
can't be scripted).

**Not changed (noted):**
- The **₹107.44** seen on the pay card comes from an old fixture; the
  backend rounds fares to whole rupees.
- **"End here" for parcels** (A.5, D7).
- **Parcel form copy** (A.9, D8).
- The new parcel safety lines are plain translations. Add
  "Don't hand it over" to `docs/SAFETY_TRANSLATION_REVIEW.md` before launch.

---

## PART 1 PROPOSAL: proof of delivery and theft prevention (awaiting approval)

A photo is evidence, not prevention. The controls that actually prevent
fraud are a **delivery code the partner can't know** and **money that isn't
released until delivery is confirmed**. The audit changes one assumption:
the sender is **already charged after delivery**, not at booking. That makes
"capture at booking" a payment-flow change; see D1.

### Layers

1. **Delivery code.** Generated at ACCEPTED, shown only to the sender in the
   app (same read path as the pickup code). She passes it to the receiver;
   by SMS too once DLT is live. COMPLETED for a parcel needs the code (5
   attempts, then locked) or the exception flow. Never on the partner's
   payload.
2. **Pickup photo.** Required before IN_PROGRESS for a parcel: the parcel as
   handed over. Camera only, compressed on the device to about 1280 px and
   about 70% JPEG. The server stamps its own time and her last trusted GPS
   fix.
3. **Delivery photo.** Same rules, at the door or handover, never the
   receiver's face; the app says so in one line. Required for completion by
   code too.
4. **Geofence becomes a gate for parcels.** "Delivered" needs ≤
   `PARCEL_DELIVERY_RADIUS_M` (150). Outside it, the partner must give a
   reason **and the booking is flagged for review**; never silent.
5. **Exception flow (receiver absent or refuses).** New state
   `DELIVERY_EXCEPTION`: reason plus photo plus a logged in-app chat to the
   sender. The sender chooses: **Wait** (up to N min), **Leave with someone
   I name** (name required, photo of the handover), or **Return to me**
   (return trip; fare is D2). Free text alone never completes a delivery.
6. **Money.** The partner's share for a parcel goes into a `held` balance,
   not `available`, until delivery is confirmed and
   `PARCEL_DISPUTE_WINDOW_HOURS` (24) pass with no complaint. An admin can
   hold it longer with a reason. No cash on delivery in the pilot: COD makes
   the partner carry the receiver's money, adds a second thing to steal and
   reconcile, and cash is already refused platform-wide.
7. **Parcel rules.** Optional declared value up to `PARCEL_MAX_DECLARED_VALUE`;
   a prohibited-items list the sender ticks once per booking; size and
   weight limits that fit a bike. You set the numbers (D3–D4).
8. **Admin evidence screen.** On the booking detail: delivery-code events
   (generated, attempts, verified or locked), both photos (signed links),
   trail, pickup and drop times, geofence result, exception steps, plus
   **Hold partner earnings** and **Open dispute**. Each is audited and needs
   a reason.
9. **Photo storage.** Same private storage; links valid 1–2 hours, handed
   only to the sender, the assigned partner and admins with TRIPS_VIEW.
   Delete after `PARCEL_PHOTO_RETENTION_DAYS` (90), unless a dispute is open,
   in which case they're kept until it closes. Draft privacy-policy
   sentence, for your lawyer: *"When SheOut delivers a parcel, your partner
   photographs the parcel at pickup and at delivery, with the time and her
   location. We use these photos only to confirm the delivery and to
   resolve complaints. Only you, your partner and SheOut's operations team
   can see them, and we delete them after 90 days, or when a complaint about
   the delivery is closed, if that is later."*
10. **Deterrents.** Geofence overrides, code lockouts, exceptions and upheld
    disputes are counted on her record in the admin console, with a flag
    above a threshold. **No automatic deactivation.**

### Fraud and theft cases

| Fraud / theft case | Layer that stops it | What is left unprotected |
|---|---|---|
| Partner takes the parcel and never delivers | (1) no code means no COMPLETED; (6) nothing released; trail and pickup photo identify the parcel | The parcel is gone. Recovery is police plus account action; declared value only frames compensation (D5) |
| Partner marks "Delivered" without delivering | (1) code required; (4) geofence; (3) photo | Collusion with the receiver; a receiver who hands the code over without the parcel |
| Partner swaps or opens the contents | (2)+(3) photos of the outer packaging | Tampering inside sealed packaging is invisible to a photo |
| Partner delivers to the wrong person | (1) only the right receiver has the code | The sender gives the code to the wrong person |
| Receiver gets it, then claims it never arrived | (1) code-verified event; (3) photo; trail; geofence result | A receiver claiming damage that happened before pickup (the pickup photo helps) |
| Sender claims it was damaged or never collected | (2) pickup photo with server time and GPS | Damage hidden inside the packaging |
| Fake "receiver absent" to skip the trip | (5) photo and chat required; the sender decides; repeats raise a flag (10) | One-off abuse costs the sender time |
| Partner uses a gallery photo or an old photo | Camera-only capture; server timestamp and GPS at upload; repeated photo hashes flagged | A modified phone can fake a camera feed; GPS spoofing is only partly caught by the jump detector (`DispatchService.java:134-153`) |
| Prohibited or dangerous item sent | (7) acknowledgement with timestamp; partner can refuse at pickup | Nothing physically inspects the parcel |
| Money: partner paid for an undelivered parcel | (6) held balance until confirmed plus 24 h; admin hold | Payouts already made before a late complaint (outside the window) |
| Sender ends it herself with "End here" | — (D7) | Today this completes and charges with no delivery proof |

### Decisions for you (Part 1)
- **D1 Payment timing.** Keep pay-after-delivery (recommended: the sender
  never pays for something not delivered, and it's the current flow), or
  capture at booking (needs a refund path for every cancelled or failed
  delivery).
- **D2 Return fare.** Who pays for a parcel returned to the sender, and how
  much.
- **D3 Limits.** Max declared value (suggestion: ₹5,000), weight (suggestion:
  10 kg), size (suggestion: 40×40×40 cm, must fit the bike's box or bag).
- **D4 Prohibited items.** Suggested: cash and jewellery; drugs and alcohol;
  weapons; flammables and gas; live animals; perishable food beyond 2 h;
  documents of legal value; anything illegal.
- **D5 Liability.** Whether declared value means compensation, and up to
  what amount.
- **D6 Retention.** 90 days, and the privacy sentence above.
- **D7 "End here" for parcels.** Hide it for deliveries (recommended), or
  keep it.
- **D8 Parcel form copy.** Change "she can call you" to "she can message
  you".

---

## PART 2

See `docs/queued-dispatch-design.md`.
