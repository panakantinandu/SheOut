# Launch gate

What has to be true before SheOut takes real customers, and how to check it.
Run:

```
node scripts/launch-gate.mjs                         # repository only
node scripts/launch-gate.mjs --live                  # also what the live sites, content API and DNS serve
node scripts/launch-gate.mjs --env-file prod.env     # also production switches (see below)
```

It prints PASS, FAIL or MANUAL for every item below and exits 1 while
anything fails. **MANUAL** means a person has to confirm it; the script
cannot see it.

**The script only reads.** It reads repository files, runs the repo's own
check scripts and `npm audit` (never `--fix`), and with `--live` makes GET
requests to the public sites and the public content API, plus one DNS
lookup. It never signs in, calls an admin API, writes a file or changes a
setting.

`--env-file`: production switches live in Render, which the script cannot
read. To check them, the owner copies the variables named in section C from
Render (Dashboard > sheout-backend > Environment) into a local file as
`KEY=value` lines and passes it. Only the section C keys are read, and no
value is printed: only "as wanted" or "differs" (for Razorpay, only whether
the key id is a live one). **Never commit that file.**

## The gate

### A. Business details and legal pages

| Item | Passes when | Owner |
|---|---|---|
| Business details | No `TODO_FROM_OWNER` in `legal-entity.config.json` (CIN, registered address, phone, grievance officer's name) | Founder (O-2, O-3, O-4) |
| Strict build | `LEGAL_STRICT_BUILD=true` on all three Vercel projects, so a placeholder can never ship (MANUAL) | Founder |
| Legal pages final | No `DRAFT, pending legal review` and no `[to be reviewed by lawyer]` marks left in `design-system/src/legal/content.ts` | Lawyer, then engineering |
| Lawyer sign-off | Every row of "D. Lawyer sign-off" in `docs/legal-open-items.md` reads `Approved YYYY-MM-DD`, with who approved and where the written approval is kept: 11 documents plus the court-record screening process | Lawyer |
| Hindi and Telugu | Same table, second column: done only after the English is approved, checked by a fluent reader | Engineering + reviewer |

### B. What the apps and the site say

| Item | Passes when |
|---|---|
| Removed claims (audit D-1) | No "police verified", "police-verification review", "background verified/checked", "100% safe" or "trusted by N" in the apps' en/hi/te text, the legal pages, the public site, or a content block seeded by a migration |
| Legal sync | `scripts/check-legal-sync.mjs` passes: versions and refund days agree between the pages and the server |
| Built bundles | The same check over the three production builds |
| Public site | `scripts/check-marketing-assets.mjs --strict`: no rating, review or user counts, no placeholders, and no screenshot still marked as an old capture in `screens.json` |
| Insurance | The app's "Every SheOut trip is insured" card is only true while a ride policy is active in the console and `INSURANCE_REQUIRED_FOR_RIDES=true` (MANUAL, or `--env-file` for the switch) |

### C. Production switches (Render)

| Variable | At launch | Why |
|---|---|---|
| `DEV_MODE_ENABLED` | `false` | Test sign-in codes and test numbers work while it is on (the live test accounts use it today) |
| `LOG_OTP_CODES` | `false` | Sign-in codes in the logs |
| `DEV_OTP_NUMBERS`, `DEV_OTP_TEST_PREFIX` | blank | Fixed-code test numbers |
| `PROMOTIONAL_MESSAGES_ENABLED` | `false` | No opt-in yet (O-10) |
| `ADMIN_COURT_SCREEN_ENABLED` | `false` | Until the lawyer signs off the court-record check |
| `ADMIN_PHONE_LOGIN_ENABLED` | `false` | Staff use passkeys |
| `MARKETPLACE_SAMPLE_SHOPS` | `false` | Sample shops are not real; photo sources unknown (O-20) |
| `RETENTION_ENABLED` / `RETENTION_DRY_RUN` | `true` / `false` | After the O-8 periods are approved and a dry-run report is checked |
| `INSURANCE_REQUIRED_FOR_RIDES` | `true` | Rides pause when no policy is active |
| `RAZORPAY_KEY_ID` | a `rzp_live_` key, UPI on | Real payments |
| SMS provider | live, with DLT templates | Sign-in codes and SOS texts to contacts (today: none) |

Queued offers are not built, so they have no switch to check.

### D. Security

| Item | Passes when |
|---|---|
| Spring Boot | Not 3.0-3.4 (out of open-source support). Branches `chore/spring-boot-upgrade` (3.5.16) and `chore/spring-boot-4-1` (4.1.1) wait for review |
| Backend CVEs | The OWASP report (`backend/target/dependency-check-report.json`) has no critical or high findings and is under 7 days old. Re-run on the release branch |
| Frontend advisories | `npm audit --omit=dev`: no critical or high |
| Headers | `vercel.json` of each app sets `X-Content-Type-Options`, `Referrer-Policy` and frame protection; `--live` checks they are actually served, with HSTS |
| CSP | Enforced on both apps, not report-only (O-23: after a preview deploy with maps and checkout open shows no violations) |

### E. People and paperwork (MANUAL unless stated)

| Item | Passes when |
|---|---|
| Screen-reader pass | A person ran `docs/screen-reader-checklist.md` with NVDA and TalkBack or VoiceOver and recorded it in its Results table (checked by the script) |
| Mailboxes | `support@`, `grievance@`, `privacy@` receive a test mail from outside; someone reads `grievance@` within 48 hours. `--live` checks the domain has MX records |
| DPAs | Processors' data-processing terms accepted and filed (O-15) |
| Breach response | A named person notifies the Data Protection Board within 72 hours (`docs/incident-response.md`) |
| Company | Bank account, GSTIN and payment gateway in the operating company's name |

## Result on 2026-10-15

Run on branch `docs/launch-gate` (cut from `chore/marketing-assets-refresh`)
with `--live`: **11 pass, 28 fail, 18 manual. Not ready.** The failures:

- **Legal:** placeholders for CIN, address, phone and grievance officer; 11 DRAFT and 27 lawyer-review marks; no document signed off (12 rows).
- **Claims:** the customer app's About screen still says partners pass a "police-verification review": `about.feature1Body` in `customer-app/src/i18n/{en,hi,te}.json`, and content block `about.feature.1.body` (V17), **which production serves today**. Missed by Phase 1. Fix: new wording in the three i18n files and a migration updating the content block (the D-1 wording: "Every partner's ID, selfie and licence are checked by a person").
- **Public site:** six map screenshots are still old captures (`docs/marketing-assets.md`). The live site still says "police verified": Phase 1 is not deployed.
- **SMS:** no provider live.
- **Security:** this branch is on Spring Boot 3.3.4; the last OWASP report (from the 4.1.1 build) has 1 critical and 9 high; npm audit has 4 high (firebase; react-router is fixed on `chore/react-router-advisory`); CSP is report-only on both apps. **Live, the customer and partner apps serve no security headers at all:** the Phase 1 headers are not deployed.
- **People:** no screen-reader pass recorded. The checklist is on `test/a11y-booking-flow`, not merged into this branch.

The order to merge them in, and the staging checklist to run first, are in `docs/release-plan.md`. Several failures clear when the branches are merged and deployed; the rest
need the owner, the lawyer or a person with a screen reader.
