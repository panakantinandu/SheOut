# Legal open items

Two lists: what the founder owes the build, and what to ask the lawyer.
Each item points back to `docs/legal-audit.md`. Until an item is answered, the
code keeps the safe default named here and shows a visible placeholder. It
never shows an invented value.

Every legal page is a **DRAFT, pending legal review** until the lawyer signs
off. Each page will say so in a code comment, and this file lists it below.

## Status after Phase 1 (2026-10-15)

**Answered and built** (see `docs/legal-privacy-a11y-report.md`): O-1 (name:
Snivika Technology Private Limited), O-4 in part (email
`grievance@sheoutride.com`; name pending, must be India-based), O-5, O-6
(police claims removed), O-7 ("Sample screens" labels), O-10 (promotions
paused until an opt-in exists), O-11 (illustrations replaced), O-12
(wallet by default; original method for failed, duplicate and
SheOut-cancelled payments within 7 working days), O-8 (jobs built, off).

**Still owed by you:**

| # | What | Blocks |
|---|---|---|
| O-2 | CIN, registered office address | The strict build (`LEGAL_STRICT_BUILD=true`) and the footer |
| O-3 | Public phone number; create the `support@`, `grievance@` mailboxes in Workspace | Same |
| O-4 | Grievance Officer's name (India-based) | Same |
| O-8 | Approve the retention periods, then switch the jobs on as a dry run. **Owner's decision 2026-10-15: jobs stay OFF until the lawyer has reviewed the periods** | Retention |
| O-13 | The prohibited parcel items list (today's server list is shown) | Parcel Rules sign-off |
| O-14 | Is `SENTRY_DSN` set on Render? Is `ANTHROPIC_API_KEY` set? | The Privacy Policy's processor list (`processors[].live` in `legal-entity.config.json`) |
| O-16 | Self-host the Google Fonts? | IP addresses reaching Google before sign-in |
| O-19 | Re-shoot the marketing screenshots and videos from the current apps, with the new art and no rating. **Done on 2026-10-15 except the six map screens**, which need the Maps key (`docs/marketing-assets.md`) | Removing the "Sample screens" labels; the last unlicensed art (`docs/assets-licences.md`) |
| O-20 | Where the 26 sample-shop photos came from (kept, as you asked) | Turning sample shops on in front of real customers |
| O-21 | Who drew the SheOut logo, and that the rights belong to the company | Trademark filing |
| O-22 | **Upgrade Spring Boot 3.3.4** (out of support) on its own branch: the OWASP scan of 2026-10-15 finds 43 critical and 111 high CVEs, almost all in its managed libraries (Netty, Tomcat, Spring) | Security |
| O-23 | When to enforce the app CSP (now report-only) - after a preview deploy with maps and checkout open shows no violations | Security headers |
| O-24 | The customer app's About screen and Safety Centre said every partner passes a "police-verification review". **Fixed on `fix/about-claim-o24` (6bd4c2b)**: en/hi/te wording, assistant knowledge, and migration V84 for the content block production serves. Live only after merge and deploy. The same scan found SOS wording that says SheOut texts emergency contacts while no SMS provider is live (`docs/claim-scan-2026-10-15.md`, section 1) - for your decision | Claims (D-1) |

**For the lawyer, added in Phase 1:** the court-record check as built (consent
wording `COURT_SCREEN_CONSENT`, the rejection categories, 30-day and 12-month
retention); "safe rides" wording; the marketing site's helmet line ("brings a
spare helmet for you", not enforced by the app); Partner Terms in full.

## A. Inputs and decisions from you (founder)

| # | What | Why it is needed | Safe default until then | Proposed (for you to accept or change) |
|---|---|---|---|---|
| O-1 | **Exact registered name** of the parent company, as on the Certificate of Incorporation. You wrote "Snivika Technologies Pvt. Ltd."; the brief says "Snivika Technology Private Limited" | Footer, policies, invoices, Razorpay | Placeholder, production build fails | - |
| O-2 | CIN, registered office address, GSTIN (when issued) | E-Commerce Rules r.4; DPDP contact; Razorpay | Placeholder, production build fails | - |
| O-3 | Public contact email and phone. Does `support@sheoutride.com` exist in Workspace? | Footer, Contact page | The marketing footer already shows `support@sheoutride.com` | Create `support@`, `grievance@` and `privacy@` (aliases are fine) |
| O-4 | **Grievance officer**: name, designation, email, phone | E-Commerce Rules r.4(4); DPDP | Placeholder; the apps say "not yet available" | You, as founder, until someone is hired, with `grievance@sheoutride.com` |
| O-5 | Brand line | Every page | "SheOut, a brand of Snivika Technology Private Limited" (one config string) | Change it in one place once the CA settles the structure |
| O-6 | **Claims wording** on the marketing site (audit D-1) | Misleading-advertising risk | No change until you approve | "Every partner's ID, selfie and licence checked by a person"; "Fare fixed before you book (it changes only if you change the drop)"; drop "police-verified" until every live partner has a certificate on file |
| O-7 | Marketing screenshots and video show a 4.9 rating and sample trips | Fake rating | No change until you choose | Re-shoot with the rating hidden. The other option is a label on every frame: "Illustrative screen, sample data" |
| O-8 | **Retention periods** | DPDP; Privacy Policy | Jobs built but **off** | Chat 90 days after the trip; shift selfies 30 days; verification selfie and ID images: while verified plus 30 days after the account closes; rejected applications 30 days; trip coordinates rounded to ~100 m after 12 months; SOS records 3 years; staff and access logs 1 year (DPDP minimum) then deleted; trip and payment records 8 years (Companies Act and GST, CA to confirm); parcel photos 90 days (already live); court-record screening 12 months after she leaves, 30 days after a rejection unless disputed; inactive accounts: no auto-deletion |
| O-9 | Date of birth: keep the full date, or only "18+ confirmed" plus the year? | Data minimisation | Full date kept | Keep the full date for partners (it is on the licence), year only for customers |
| O-10 | Marketing messages opt-in: which channels (push, email, WhatsApp later)? | Consent | Nothing changes until approved | One unticked "Offers and news from SheOut" box covering push and email; "We miss you" pushes only for those who ticked it |
| O-11 | **Source and licence of the four illustrations** (`design-system/src/assets/art/*.webp`) and whether voiced explainer videos will be published | Copyright | Kept, recorded as "unknown" | Tell me who made them (you, a designer, an AI tool and which one). For the voices, use a commercially licensed TTS service or a recorded voice |
| O-12 | **Refund rules**: refund to wallet only (today), or back to the card/UPI as well? Time to refund? Cancellation fee? | Refund policy page; Razorpay activation | Policy page states today's rule only | Refunds to the original method within 5-7 working days once Razorpay's refund API is built; wallet credit instantly if she chooses it. No cancellation fee before a partner accepts; ₹0 fee for now (the code charges none) |
| O-13 | Prohibited parcel items list | Parcel Rules page | The server-versioned list in the parcel sheet | Add to the existing list: cash and jewellery over ₹5,000, alcohol, drugs and medicines without prescription, weapons, explosives and flammables, live animals, perishables over 4 hours, documents of others' identity, anything illegal |
| O-14 | Is `SENTRY_DSN` set on Render production? | Third-party list | Unknown | Tell me, or let me read the Render environment names |
| O-15 | Accept the processors' DPAs (Render, Vercel, Google Cloud/Firebase, AWS, Anthropic, Razorpay) | DPDP processor contracts | - | Most are click-through. File copies in the company drive |
| O-16 | Fonts: self-host the Google Fonts (no visible change)? | IP leak to Google | No change | Yes |
| O-17 | Move documents or the database to an Indian region? | Data location | Singapore, disclosed | Not now. Disclose, and revisit when the company account exists |
| O-18 | Court-record screening: response window before a final rejection | Phase 1 item 7 | Flag off | 7 days |

## B. Questions for the lawyer

1. **Operating legality:** Can SheOut run bike taxis on private (white-plate)
   two-wheelers in Telangana today? Has Telangana adopted the Motor Vehicle
   Aggregator Guidelines 2025? Which aggregator licence is needed, and from
   which date?
2. **Women-only service:** How should eligibility be worded and enforced (ID
   review by a person) so it is lawful and not unfair-trade-practice?
3. **Partner classification:** Is the partner-independence wording safe? What
   are the platform-worker obligations under the Code on Social Security 2020
   and any Telangana gig-worker law?
4. **Police verification:** Is a private background check acceptable instead
   of a police certificate (decision #7 in `PENDING_DECISIONS.md`)? Is the
   manual court-record screening (Phase 1 item 7) lawful as designed? What must
   the partner be told, and how long may results be kept?
5. **Biometrics:** Wording for selfie and face-match consent; retention; is
   on-device matching plus AWS Rekognition (Mumbai) disclosed sufficiently?
6. **Privacy Policy:** Review the rewritten draft. Confirm SheOut's role
   (fiduciary) and each vendor's (processor or fiduciary, e.g. Razorpay).
   Confirm the cross-border disclosure (Singapore, USA). Confirm the DPDP dates
   (13 May 2027 for Rules 3, 5-16, 22, 23) and whether the reported proposal to
   shorten them has been notified.
7. **Terms:** Limitation of liability, SOS disclaimer, governing law and
   courts (proposed: India, courts at Hyderabad), arbitration or not.
8. **Wallet:** Is the SheOut wallet a closed-system prepaid instrument outside
   RBI PPI licensing? Can refunds be wallet-only?
9. **Consumer Protection (E-Commerce) Rules 2020:** Do they apply to rides and
   parcels (services) and to the Marketplace listing model? What must the
   Marketplace show about sellers?
10. **Marketing:** "Police-verified", "Rides by women, for women", "SOS on
    every trip", "Fare fixed before you book". Which may be used, and with what
    qualifiers?
11. **Parcels:** Liability cap, prohibited items, the receiver-code rule,
    photo retention (90 days).
12. **Seller terms:** See `docs/LEGAL_REVIEW.md` (discount honesty, non-party
    to sales, showing sellers' numbers).
13. **Incident response:** Who notifies the Data Protection Board, and the
    breach-message template for users.
14. **Trademark:** File "SheOut" (word and logo) in the relevant classes.
15. **Copyright holder** in `LICENSE` and `NOTICE` once the company owns the
    code (`PENDING_DECISIONS.md` #20).

## C. Pages that are drafts pending legal review

Filled in during Phase 1. Each will carry `DRAFT, pending legal review` in a
code comment.

| Page | File | Status |
|---|---|---|
| Privacy Policy | `frontend/design-system/src/legal/content.ts` | Existing draft; rewrite in Phase 1 |
| Terms of Use (customers) | same | Existing draft; fixes in Phase 1 |
| Partner verification consent | same | Existing draft, every paragraph marked |
| Partner Terms | - | Phase 1 |
| Refund and Cancellation Policy | - | Phase 1, after O-12 |
| Cookie and Storage Policy | - | Phase 1 |
| Parcel Rules | - | Phase 1, after O-13 |
| Safety and Community Guidelines | - | Phase 1 |
| Grievance Redressal | - | Phase 1, after O-4 |
| Contact and business details | - | Phase 1, after O-1 to O-3 |

## D. Lawyer sign-off

The lawyer's written approval of each document, recorded here and nowhere
else. `scripts/launch-gate.mjs` reads this table: a row passes only when its
English column reads `Approved YYYY-MM-DD` (and, once that is done, its Hindi
and Telugu column too). Hindi and Telugu versions are made only after the
English is approved (Part B), and are checked by a fluent reader before
they count. Record who approved and where the written approval is kept.

| Document | English | Hindi and Telugu | Approved by, and where the approval is kept |
|---|---|---|---|
| Privacy Policy | Not approved | Not started | |
| Terms of Use | Not approved | Not started | |
| Partner Terms | Not approved | Not started | |
| Refund and Cancellation Policy | Not approved | Not started | |
| Cookie and Local Storage Policy | Not approved | Not started | |
| Parcel Rules | Not approved | Not started | |
| Safety and Community Guidelines | Not approved | Not started | |
| Grievance Redressal | Not approved | Not started | |
| Contact and business details | Not approved | Not started | |
| Consent to verification (partners) | Not approved | Not started | |
| Consent to a court-record check | Not approved | Not started | |
| Court-record screening process (reviewer rules: the court-record section of `docs/legal-privacy-a11y-report.md`) | Not approved | n/a | |
