# Claim scan, 2026-10-15

A scan of everything SheOut says to people, after fixing O-24. It covered:

- the customer, partner and design-system text in en/hi/te, including the safety files;
- the public site and the legal pages;
- notification and email copy (`NotificationCopy`, the staff email service, every user-visible string literal in the backend);
- the assistant's knowledge file;
- the seed data in every migration and the sample-shop catalogue.

It looked only at text a person can read: string values and JSX text, not
comments, logs or identifiers. The scanner is `D:\_qa-scratch\claim-scan\scan.mjs`.

## Fixed on `fix/about-claim-o24`

| Where | Was | Now |
|---|---|---|
| Customer About screen, `about.feature1Body` en/hi/te | "Every partner passes an ID and police-verification review" | "A person checks every partner's ID documents and driving licence before her first trip, and she passes a live face check at the start of every shift." |
| Content block `about.feature.1.body` (what production serves) | Same claim (V17) | Same new wording, by `V84__about_partner_checks_wording.sql`. It replaces any value that mentions the police, even an operator's edit |
| Customer Safety Centre, `verification.partners` en/hi/te | "SheOut's team also records a police verification for every partner" | Licence and RC checked by a person; live selfie matched at the start of every shift |
| Customer Safety Centre, `verification.people` en/hi/te | "nothing matches faces by itself", which the shift check contradicts | Every *approval* is by a person; the one automatic check is the shift selfie, with a person reviewing repeated misses |
| Assistant knowledge, partner checks | "records a police verification for every partner" | The same facts, plus "do not say partners are police verified or background checked" |
| CI bundle check (`check-legal-sync.mjs`) | - | Fails on "police-verification review" and "records a police verification" (proven: fails on the old build, passes on the new) |

"Live face check" refers to the start-of-shift selfie that the phone matches
against her verified photo (`SHIFT_CHECK_ENABLED`, on by default). The
server-side comparison (`FACE_CHECK_ENABLED`, off by default) is not
promised. If `SHIFT_CHECK_ENABLED` is ever switched off in production, this
wording stops being true.

The Hindi and Telugu wording is my draft. A fluent reader should check it.

## Found, not changed (for your decision)

### 1. SOS texts to emergency contacts - **fixed on `fix/sos-sms-wording`**

Owner's go-ahead 2026-10-15. Every place below now says that SheOut's team is
alerted at once, that SheOut cannot text the contacts yet, and that the SOS
screen gives a one-tap button to text them from her own phone. The changes:

- en/hi/te text in the app;
- the public site's two lines;
- the assistant's steps, which also say "never say SheOut has texted them";
- the two content blocks, by `V85__sos_contacts_wording.sql`, with a migration test;
- the CI bundle check, which fails on the old phrases.

**When the SMS provider goes live, change this wording back** and remove the
four SOS patterns from `check-legal-sync.mjs`. The Hindi and Telugu are my
drafts, to be checked by a fluent reader.

What the scan found before the fix:

No SMS provider is live (DLT pending). Today, SOS alerts SheOut's team and
opens the customer's **own** SMS app with the message ready, and she presses
Send. The Terms already say this correctly (`legal/content.ts:260`). These
places say SheOut sends the texts:

| Where | Text |
|---|---|
| Customer About, `about.feature2Body` en/hi/te, and content block `about.feature.2.body` (V17) | "one tap on SOS sends them your location by SMS" |
| Customer FAQ, `en.json:290` en/hi/te, and the V17 FAQ answer to question 4 | "The emergency contacts saved on your account are sent an SMS with your live location" |
| Emergency contacts screen, `en.safety.json:7` en/hi/te | "If you raise an SOS, everyone here is sent an SMS with your location." |
| Safety Centre SOS steps, `en.safety.json:71` en/hi/te | "SheOut texts every emergency contact you have saved" |
| Public site, `marketing-site/src/content.ts:125` | "messages your emergency contacts" |
| Assistant knowledge, SOS steps 2-3 | "SheOut texts every emergency contact ... an SMS" |
| Sign-up, `en.json:116` | "your emergency contacts are texted from it if you press SOS" (ambiguous) |

The fix, as suggested: say what happens today ("SOS alerts SheOut's team and opens
your SMS app with a message to your contacts, ready to send"), and switch the
wording when the SMS provider goes live. The fix needs a content-block
migration like V84.

### 2. Insurance - conditional, correct as built

The customer's "Every SheOut trip is insured" card appears only while a
passenger policy is active (`InsuredTripsExplainer.tsx`). The claim is fully
true only with `INSURANCE_REQUIRED_FOR_RIDES=true` (rides pause without
cover); the launch gate checks it. "Parcels are not insured" is stated in
Parcel Rules.

### 3. Police wording elsewhere - correct

The partner app, partner notifications, the public site's partner FAQ, the
partner FAQ content block (V17) and the legal pages say a partner must
**send a police verification certificate**. That is a requirement the code
enforces, not a claim made to customers. The court-record pages say "a
screening, not police verification". The only police wording a customer now
sees is "call 112 for police, an ambulance or fire".

### 4. Nothing found

- **Ratings:** no rating numbers.
- **Counts:** no user or ride counts and no "trusted by".
- **Availability:** no "24/7" or round-the-clock.
- **Payouts:** no "instant refund or payout".
- **Guarantees:** no guarantee or "100% safe". The "100%" hits are CSS and a discount validation message.
