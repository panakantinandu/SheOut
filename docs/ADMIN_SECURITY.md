# How the ops console is protected

Written for SheOut's founders. No technical background needed.

## Why this matters

The ops console can see every woman's identity documents, police
certificates, home and work addresses, live location, phone number and
payment details. It can block accounts, approve partners and mark payouts
paid. It is the most dangerous part of SheOut. The rule this work follows:
**one stolen staff login must never expose everything or move money.**

## Who can get in, and how

- **Only people you invite.** Nobody can sign up. An owner (or, for junior
  roles, the operations manager) types in a work email and picks a role.
  That person gets a link that works once, for 24 hours.
- **Three things to sign in:** a work email, a password, and a 6-digit code
  from an authenticator app on their phone (Google Authenticator, Microsoft
  Authenticator and similar). **No SMS codes for staff.** SMS codes can be
  stolen by swapping someone's SIM or tricking them into reading one out;
  that is how most ops consoles are broken into.
- **Passwords** must be at least 12 characters and cannot be one of the
  passwords attackers try first. Nobody is forced to change theirs every few
  months; that only makes people pick worse ones.
- **A lost phone:** at setup everyone saves 10 one-time recovery codes on
  paper or in a password manager. Each one gets them in once. If they lose
  those too, an owner resets their sign-in and they set it up again.
- **Guessing doesn't work:** after 5 wrong tries an account is locked for
  15 minutes, and the message never says which part was wrong.

## What each role can do

Each person has one role. The role decides what they can open; the server
checks it on **every** request, so hiding a button is never the only lock.

| Role | For | Can | Cannot |
|---|---|---|---|
| Owner | You (ideally two of you) | Everything | Lock themselves out by removing the last owner |
| Operations manager | Your ops head | Run every queue, safety cases, block/unblock, reports, manage junior staff | Add managers or owners, change insurance or settings, mark payouts paid |
| Verification agent | Onboarding staff | Check IDs, selfies, partner documents, police evidence | See payments, payouts, trips or riders' accounts |
| Support agent | Customer care | Answer support tickets | See ID documents or police certificates |
| Safety responder | The 24x7 safety desk | SOS alerts, trip alerts, route reviews | See payments or documents |
| Finance | Accounts | Payout requests, insurance premiums, payments | See ID or police documents; mark payouts paid on their own |
| Marketplace moderator | Seller desk | Review seller shops | Anything about rides, partners or money |
| Auditor | Your CA, for a fixed period | Read finance reports | Change anything; access ends by itself on the date you set |

The exact list of permissions is printed in
`backend/src/test/resources/staff/permission-matrix.txt`. If anyone changes
a role, the build fails until that file is changed too, so the change shows
up for review.

## Sessions

- A sign-in lasts **one shift (8 hours)** at most, and ends after **30
  minutes with nothing done**. Owners are signed out after 15 quiet minutes;
  the night-shift safety desk gets 60 minutes and 12 hours. The console
  warns 2 minutes before. Screens
  that refresh themselves do not count as someone being there, so a forgotten
  open tab still signs itself out.
- Everyone can see where they are signed in and sign out everywhere from
  **My account**.
- The sign-in is kept in a way the page itself cannot read, so even a bug
  in the page cannot leak it.

## Personal details are hidden until someone gives a reason

Everywhere in the console, phone numbers show as `98•••••210` and addresses
as their area ("Banjara Hills, Hyderabad"). That includes you. To see one in
full, a person must:

1. be in a role allowed to (owners and the operations manager; the safety
   desk for phone numbers only),
2. type the code from their authenticator app again (good for 5 minutes),
3. type why they need it.

The safety desk can only do this for people in a trip with an open SOS or
trip alert, or one closed in the last 30 minutes. Every reveal, with its
reason, goes in the audit log; more than 20 in 10 minutes by one person
alerts you straight away.

## The code again, before anything serious

Opening an ID or police document, marking a payout paid, changing anyone's
role or access, changing an insurance policy, and every download of data
all ask for the authenticator code again, even in the middle of a shift.
A session left open on a shared desk is not enough on its own.

## Everyone sees only their own work

A verification agent "takes" a partner from the queue before opening her
documents, and nobody else can open that partner meanwhile. A support agent
sees unassigned tickets and her own. The operations manager and owners see
everything and can hand work back to the queue.

## The audit log

Owners have an **Audit** page: who did what, when, from where, and why -
every change, every document opened, every reveal, every refusal, every
sign-in. Nobody can edit or delete a line, and each line is sealed to the
one before it, so a line changed behind the scenes (by someone with the
database password) is detected by a check that runs every night. If that
check ever fails, you are alerted at once.

You are also alerted (on your browser, and by email once email is set up)
when an account is locked after wrong passwords, someone's role or access
changes, data is downloaded, someone reveals a lot of details quickly, or
someone signs in from a browser they have not used before.

## When someone leaves

Open **Staff**, choose **Disable**, and type why. They are signed out of
every browser at once and stop getting SOS alerts. Only an owner can bring
them back.

## Setting up the first owner

In the Render dashboard, set `SHEOUT_OWNER_BOOTSTRAP_EMAIL` to your work
email (it is not written anywhere in the code) and `STAFF_SECRETS_KEY` to a
new random key.


When SheOut first starts with these changes, it emails an invitation to the
address you set as `SHEOUT_OWNER_BOOTSTRAP_EMAIL`. Email is not set up on
the server yet, so the link is written into the server's log instead: open
Render, the `sheout-backend` service, Logs, and search for
`STAFF OWNER INVITATION`. Open it within 24 hours. Then invite a second
owner straight away.

## What is coming next

This is phase 1 of 4.

1. **Done:** staff accounts, authenticator codes, invitations, safe sessions,
   roles checked on every request, phone sign-in for the console switched off.
2. **Done:** phone numbers and addresses masked until someone gives a reason;
   the code again before sensitive actions; agents seeing only their own
   work; a tamper-evident record of every look and every action; alerts to
   owners.
3. Two people needed to mark payouts paid, approve big refunds, add a
   manager or owner, or change commission, fares, insurance or GST.
4. Dashboards for each role, a live operations board with instant SOS
   alerts, work queues with assignment and time limits, passkeys for owners.

## What you decided (3 October 2026)

1. **Owners:** two - you now, a second later. You set
   `SHEOUT_OWNER_BOOTSTRAP_EMAIL` yourself in the Render dashboard.
2. **Refunds:** support agents up to ₹200, the operations manager up to
   ₹1,000; anything above needs an owner. (Finance was not given a figure
   and gets ₹200 until you choose one.)
3. **Network restriction:** off for now. It is built; turning it on is one
   setting per role (`STAFF_IP_ALLOWLIST_OWNER`, `..._FINANCE`, ...).
4. **Session lengths:** 30 minutes idle and 8 hours for most; the safety
   desk 60 minutes and 12 hours; owners 15 minutes and 8 hours.
5. **Your CA:** can be given an auditor account, read-only finance, which
   ends after 30 days unless you pick another date. None exists yet.
