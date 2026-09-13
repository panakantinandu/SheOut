# Security Policy

SheOut carries data that can put a woman in physical danger if it leaks:
her home and work addresses, her live location during a trip, her
government identity document, her phone number, and the contacts she
listed to be called in an emergency. A vulnerability here is not an
inconvenience. Please treat it accordingly, and so will we.

## Reporting a vulnerability

Email **nandupanakanti@gmail.com** with "SECURITY" in the subject.

Please include what you found, how to reproduce it, and what an attacker
could reach with it. A rough description is far better than none; do not
wait until you have a polished write-up.

**Do not open a public GitHub issue for a security problem.** An issue is
world-readable the moment you file it, and this service is live.

You can expect an acknowledgement within 3 working days and an assessment
within 10. If you do not hear back, assume the mail was lost and send it
again rather than concluding the report was ignored.

## What we ask of you

- Report privately and give us a reasonable chance to fix it before
  publishing.
- Use only accounts you control. Do not access, modify or retain another
  person's data. If you reach someone's data accidentally, stop, and tell
  us what you saw so we can assess the exposure.
- Do not run denial-of-service tests, send bulk SMS or OTP traffic, or
  test against real users' accounts. The OTP path costs real money per
  message and rate-limits real people out of their own accounts.
- Do not use social engineering or physical intrusion against anyone
  involved.

We will not pursue action against anyone acting in good faith within
these limits. There is no paid bounty programme. Credit is offered for
any valid report, unless you would rather stay anonymous.

## Scope

In scope:

- `sheout-backend.onrender.com` (the API)
- `sheout-customer-app.vercel.app`, `sheout-driver-app.vercel.app`
- The source in this repository

Out of scope, because they are not ours to authorise testing against:
Render, Vercel, Neon, Upstash, Twilio, Razorpay, OpenStreetMap and
Nominatim. Report issues in those to the provider.

## Things we already know

Reporting these again is welcome but will not be news:

- **SMS delivery is on a Twilio trial account.** OTPs to unverified
  numbers fail. Dev-OTP bypass numbers exist for testing and are
  configured by environment variable.
- **Push notifications are not implemented.** No device token is ever
  registered, so nothing targets one.
- **Payments are not settled.** Razorpay keys are unset; there is no live
  charging path.
- The in-app privacy policy and terms carry visible
  `[to be completed by SheOut]` placeholders for the registered entity,
  the grievance officer and retention periods. Those are deliberately
  blank pending a registered company, not an oversight.

## Handling of personal data

If you believe personal data has been exposed, say so explicitly in the
subject line. That changes how fast this is handled and may trigger
notification duties under India's Digital Personal Data Protection Act
2023.
