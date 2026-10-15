# Personal data breach: what to do

For whoever is on call, and the founder. A **draft, pending legal review**:
the lawyer confirms the notice wording and who signs it
(`docs/legal-open-items.md`, question 13).

## The law, as published

The Digital Personal Data Protection Rules, 2025 (Gazette G.S.R. 846(E),
13 November 2025), Rule 7, quoted from the Gazette text:

- **The people affected:** told "without delay", in plain language: what
  happened, its likely consequences for them, what we are doing about it,
  what they can do to protect themselves, and a contact who can answer
  their questions.
- **The Data Protection Board of India:** told "without delay" with a
  description (nature, extent, timing, location, likely impact), and
  "within seventy-two hours of becoming aware of the breach" a fuller report:
  the facts, the cause, what we have done and will do, any findings about
  who caused it, what stops it happening again, and a report on the
  notices sent to the people affected.

Rule 7 applies from **13 May 2027** (Rule 1(4)). Follow it now anyway: the
habit costs nothing, and the IT Act, 2000 (section 43A) and CERT-In's 2022
directions (report certain incidents to CERT-In within six hours) already
apply. The CERT-In six-hour figure is from the CERT-In directions of April 2022 as remembered, not re-checked in this pass: **the lawyer confirms it and which incident categories apply to SheOut.**

## Who decides

| Role | Who today | Does |
|---|---|---|
| Incident lead | The founder | Decides it is a breach, who is told, and signs the notices |
| Technical lead | Whoever has production access | Stops it, keeps the evidence, finds out what was reached |
| Grievance Officer | `grievance@sheoutride.com` (name pending) | Answers the people affected |
| Lawyer | To be appointed | Checks the notices, the Board report and CERT-In |

## Steps

**First hour.**

1. Stop it: rotate the leaked secret (Render dashboard; `JWT_SECRET`,
   `STAFF_SECRETS_KEY`, database password, AWS keys, Razorpay keys), revoke
   staff sessions (console, Staff), block the account or IP, take the route
   offline with a feature flag.
2. Keep the evidence: do not delete logs or rows. Export the staff audit log
   (console, Audit) and `verification_audit_events` for the window. Note the
   time you became aware - the 72 hours start there.
3. Open a private incident note (time, who, what is known, what was done).

**Within a day.**

4. Work out what was reached and whose: which tables, which fields, how
   many people. Use the data inventory in `docs/legal-audit.md` (A) to
   say which fields are sensitive (ID images, selfies, location, bank
   details, SOS records).
5. Tell the Board (the short description) and the people affected
   (template below). If CERT-In applies, within six hours of noticing.

**Within 72 hours.**

6. The fuller report to the Board.
7. Fix the cause, and write down what stops it happening again.

## Template: to the people affected

> Subject: Something happened to your SheOut data
>
> On [date] we found that [what happened, in one sentence]. It involved your
> [which data]. [What this could mean for you, honestly.]
>
> What we have done: [steps]. What you can do: [e.g. watch for calls or
> messages asking for codes; SheOut never asks for your sign-in code].
>
> If you have questions, write to our Grievance Officer at
> grievance@sheoutride.com. We are sorry.
>
> [Name], for SheOut, a brand of Snivika Technology Private Limited

Send it in English, Hindi and Telugu (the person's app language), by push
and email, and in-app. Not by SMS until DLT templates exist.

## Template: to the Board

The Board's portal and form are not published yet. Until they are, keep a
report with these headings ready: nature of the breach; extent (records,
people, fields); timing (when it happened, when we learned); location
(which system); likely impact; steps taken; steps planned; findings about
who caused it; how we stop it recurring; the notices sent to people.
