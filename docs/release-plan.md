# Merge order and staging checklist (October 2026 branches)

Everything below is **local**. Nothing has been pushed or deployed. Pushing,
merging into `main` and deploying are the owner's decisions.

## Merge order

Trial-merged on 2026-10-15, in this order, onto `origin/main` (`ae43f5a`), on
a throwaway local branch. Every step merged with **no conflicts**. On the
combined tree (with the two upgrade branches on top) the three production
builds, `check-legal-sync --bundles` and `check-marketing-assets` passed, and
the full backend suite gave **599 tests, 0 failures** (Spring Boot 4.1.1, local PostgreSQL and Memurai).

### Stage 1: merge after staging run 1 passes

| # | Branch | Head | What it brings | Migration |
|---|---|---|---|---|
| 1 | `feat/legal-privacy-a11y` | `86a1471` | Legal pages, consent, claims removed, retention jobs (off), court-record screening (off), security headers (CSP report-only), a11y fixes | V82, V83 |
| 2 | `fix/about-claim-o24` | `6bd4c2b` | About and Safety Centre partner-check wording (O-24); claim-scan report | **V84** (content block) |
| 3 | `fix/sos-sms-wording` | `f8b91d7` | SOS wording: SheOut cannot text contacts yet; one-tap text from her own phone (stacked on 2) | **V85** (content blocks) |
| 4 | `fix/whole-rupee-money` | `685d629` | No ".00" on whole amounts: Payouts, destination change, pushes/texts/emails | - |
| 5 | `feat/grievance-officer-regimes` | `d123005` | An officer per grievance regime, dated appointments, all still TODO_FROM_OWNER | - |
| 6 | `chore/react-router-advisory` | `fb40ea0` | react-router-dom 7.18.4 (advisory fix) | - |
| 7 | `test/a11y-booking-flow` | `3fa495a` | Keyboard and screen-reader fixes and journeys; screen-reader checklist | - |
| 8 | `chore/marketing-assets-refresh` | `31246c7` | Re-shot site assets, re-made seller video on the site, marketing-assets check in CI | - |
| 9 | `chore/seller-video-refresh` | `1b5b9ca` | The seller walkthrough in the customer app, re-made, en/hi/te | - |
| 10 | `docs/launch-gate` | (the commit that adds this file) | Launch gate doc and script; this plan | - |

Why this order:

- **1 first.** Every other branch was cut from it.
- **2-5 next.** Small fixes on top of 1. Branches 2 and 3 hold the only new migrations, V84 and V85, so they go in early and nothing later needs renumbering. Branch 3 is stacked on 2.
- **10 after 8.** Branch 10 is stacked on 8. Branch 9 is independent (customer app only).
- Each step is a `git merge --no-ff`, so any one of them can be reverted alone.

### Stage 2: merge after staging run 2 (with the upgrade) passes

| # | Branch | Head | What it brings |
|---|---|---|---|
| 11 | `chore/spring-boot-upgrade` | `f36b00a` | Spring Boot 3.5.16, Tomcat/Netty/Log4j pins |
| 12 | `chore/spring-boot-4-1` | `f338ba1` | Spring Boot 4.1.1 (Framework 7, Jackson 3, Hibernate 7); report with follow-ups F-1 to F-3 |

The upgrade goes last, so rolling it back never takes product fixes with it.
Merging 11 before 12 keeps "back to 3.5.16" a single revert. Neither branch has
a migration.

### Not in this plan

- **`next/drop-rc-document-key-v78`**: its migration is **V78**, below V80/V81, which `main` already has. Flyway refuses an older version on a database past it. Renumber it (V85 or above) before it is ever merged.
- **V79 (UX polish pass)**: no local branch carries it any more. If it exists elsewhere, it needs the same renumbering.
- **`feature/verification-checks`** (8 commits ahead of main) and **`fix/primary-actions-in-view`** (1 ahead): unrelated to this work; not reviewed here.

## Getting the code onto staging

`sheout-backend-staging` (Render, `render.yaml`) builds from GitHub. To test
unmerged work there, build a local integration branch (the merges above, in
order), push **that branch, not `main`**, and set the staging service's branch
to it in the Render dashboard. The run 1 integration branch is
`release/2026-10-staging` (branches 1-10 merged in order); the run 2 one is
`release/2026-10-staging-upgrade` (the same plus 11 and 12). Render does not deploy on push here, so use Manual Deploy, then prove
the new build is live (deploy log commit, and `/actuator/health/readiness`
returns 200) before testing.

Staging's CORS allows only `localhost:5173` and `5174`, so run the two apps
locally against staging:

```
VITE_API_BASE_URL=https://sheout-backend-staging.onrender.com npm run dev --workspace frontend/customer-app   # :5173
VITE_API_BASE_URL=https://sheout-backend-staging.onrender.com npm run dev --workspace frontend/driver-app     # :5174
```

### Staging settings for the run

| Variable | Value | Why |
|---|---|---|
| plan | `starter` for the run (free sleeps and is slower) | Results mean something |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | the **test-mode** pair (`rzp_test_...`) | Payments without real money |
| `RAZORPAY_WEBHOOK_SECRET` | the secret of the test-mode webhook below | Webhook checks |
| `ADMIN_COURT_SCREEN_ENABLED` | `true` **on staging only** | Section 6 |
| `RETENTION_ENABLED` | `false` (stays off, owner's decision) | - |
| `PROMOTIONAL_MESSAGES_ENABLED` | `false` | - |
| `DEV_MODE_ENABLED`, `DEV_OTP_TEST_PREFIX=+91999999`, `DEV_OTP_TEST_CODE=123456` | as in `render.yaml` | Test accounts sign in with a fixed code |

Razorpay dashboard, **test mode**:

- Add a webhook to `https://sheout-backend-staging.onrender.com/api/v1/payments/webhooks/razorpay` with the events `payment.captured`, `payment.failed` and `qr_code.credited`.
- Confirm UPI is enabled for the test account (`GET /v1/methods`). The live account once had UPI off.

Test accounts: numbers starting `+91999999` (code `123456`). Never a real
person's number. Approve their verification in the staging console.

## Staging checklist

Run it twice: **run 1** on the Stage 1 integration, **run 2** with the
upgrade on top. Mark each line Pass, or write what happened.

### 0. Deploy and smoke

- [ ] The deploy log shows the integration branch's commit; readiness answers 200.
- [ ] `flyway_schema_history` on the staging database ends at **85**, with V82 to V85 successful.
- [ ] Staff console: sign in with a passkey; a role without `verification.review` cannot open court-record checks (403).

### 1. Ride booking

- [ ] A new customer signs up (unticked Terms box must be ticked), sends ID and selfie; approved in the console.
- [ ] A new partner signs up, sends documents; approved. At the start of the shift she takes the live selfie and goes online.
- [ ] Customer: About and Safety Centre show the new partner-check wording in en, hi and te. No "police-verification review" anywhere.
- [ ] Customer books a bike ride. The fare is whole rupees and the drop time is shown.
- [ ] Partner gets the offer and accepts it. The customer sees "Arriving in about N min".
- [ ] At pickup, the partner confirms the rider; the customer reads out the code; the trip starts.
- [ ] The partner ends the trip at the drop. Both apps show the fare in whole rupees, with no ".00".
- [ ] Cancel before pickup works, with the cancellation rules shown.

### 2. Parcel trip

- [ ] The customer books a parcel: receiver name and phone, both acknowledgements ticked (size and weight under 5 kg, nothing prohibited).
- [ ] The sender sees the delivery code (receiver SMS is off).
- [ ] The partner picks up and travels. Delivery is refused more than 150 m from the drop.
- [ ] At the drop, delivery needs the photo and the code. The trip completes.
- [ ] Parcel earnings are held for 24 hours (the dispute window) before she can withdraw them.

### 3. Payment (Razorpay test mode)

- [ ] Pay a trip online with a test card or test UPI. The trip shows paid after the `payment.captured` webhook.
- [ ] A failed test payment (`payment.failed`) shows as failed, and paying again works.
- [ ] Wallet top-up through Razorpay: the balance goes up by the exact amount.
- [ ] Pay from the wallet: the balance goes down; the trip is paid.
- [ ] Partner UPI QR (`qr_code.credited`): test if test mode can pay a QR; otherwise write "not testable in test mode".
- [ ] Partner Payouts: balance and amounts with no ".00" on whole amounts; request a payout; mark it paid in the console.

### 4. Refund

- [ ] Console refund to the wallet (support role, under its limit): the wallet is credited and the push reads "₹120 refunded to your SheOut wallet" (no ".00").
- [ ] A refund above the role's limit needs owner approval.
- [ ] A refund larger than the payment is refused (`REFUND_EXCEEDS_PAYMENT`).
- [ ] To-source refund (a failed payment that took money, a duplicate, or a trip SheOut cancelled): finance refunds in the Razorpay **test** dashboard, following `docs/refunds-to-source.md`. Check no wallet credit was also given.

### 5. SOS

- [ ] During a trip, the customer presses SOS: the countdown appears with focus on Cancel. Cancel sends nothing.
- [ ] SOS sent: operators see the alert in the console at once, with location.
- [ ] The screen says no contact was reached (no SMS provider) and offers the one-tap "text from my phone" button. It opens her SMS app with the contacts and location filled in. About, the FAQ and the Safety Centre say exactly this (V85).
- [ ] Partner SOS works the same.
- [ ] An SOS nobody takes rings again: an urgent push and every open console, every 2 minutes, until someone takes it or it is resolved (`SOS_ESCALATION_*`).

### 6. Console court-record check (flag on, staging only)

- [ ] The partner sees the consent (draft wording) and agrees. Without consent, the reviewer cannot start.
- [ ] The reviewer opens eCourts in a new tab and records each search (district, name variant, date, outcome). A screenshot upload is viewable by signed link, and the view is logged.
- [ ] "Possible match" needs a second, different reviewer. The reviewer who flagged it cannot decide it.
- [ ] Before any rejection, the partner is told and sees the response window (7 days). She can answer or send a police clearance certificate.
- [ ] A decision is recorded with a reason category. The partner's police status is unchanged by the screening.
- [ ] The partner withdraws consent and the screening stops.
- [ ] The report view and the console summary show the case. Every step is in the audit log.
- [ ] With the flag off again: every court-check endpoint answers 404 and the partner sees nothing.

### 7. Webhooks

- [ ] A Razorpay webhook with a valid signature is processed (sections 3-4).
- [ ] The same webhook with a wrong `X-Razorpay-Signature` is refused (400) and changes nothing.
- [ ] The same valid event sent twice (Razorpay "resend") does not pay or credit twice.
- [ ] The verification provider webhook (`/api/v1/verification/provider/webhook`) answers 404 while `VERIFICATION_PROVIDER=manual`.

### 8. Run 2 only: the upgrade

- [ ] Sections 0-7 again on the integration plus `chore/spring-boot-upgrade` and `chore/spring-boot-4-1`.
- [ ] Logs: no `RestClientException` or `ZipException` from OSRM; route previews draw real roads; routing fallbacks stay at 0.
- [ ] API answers look the same as run 1 (dates as ISO strings, the same fields). Both apps work unchanged.
- [ ] A push reaches a test phone, for both customer and partner (firebase-admin).
- [ ] Load test (`loadtest/sheout-load.mjs`, starter plan), compared with run 1 and `docs/upgrade-4.1-report.md`: errors 0; p95 and p99 no worse; memory within the plan.
- [ ] Rollback rehearsal: redeploy run 1's commit. It starts on the same database (the upgrade has no migration).

### 9. Sign-off

| Run | Date | Commit | Who | Result | Notes |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |

After run 1 passes: merge 1-10. After run 2 passes: merge 11-12. Then run
`node scripts/launch-gate.mjs --live`.
