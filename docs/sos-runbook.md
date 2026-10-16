# SOS runbook

What an operator does when someone presses SOS. The console's **SOS Alerts**
page has a **Respond** button on every alert that shows these steps with every
number on it. This file is the same steps, for training and for when the
console is down.

**Target:** someone is on it within 2 minutes (`sosAck` in the work SLA).
After that the console escalates to every safety responder.

## Why calls, not texts

SheOut cannot send SMS yet (no DLT-approved provider). Her phone opens its own
SMS app so she can text her contacts in one tap, but SheOut itself texts
nobody. "Contacts reached" therefore reads **0** on almost every alert. That
does not mean nobody cares: it means **you** call them.

## The steps

1. **Press "I'm on it"** so others know the alert is taken.
2. **Call her.** Say: "This is SheOut safety. You pressed SOS - are you safe?
   Can you talk?" If she cannot talk freely, ask yes/no questions: "Are you in
   danger? Are you still in the vehicle? Should I call the police?" No answer:
   try once more straight away.
3. **Call 112** if she says she is in danger, cannot speak freely, or does not
   answer twice. Give her name, her phone, the location (the map link and
   coordinates on the panel) and, on a trip, the vehicle - **Where is the trip
   now** shows it live. Then keep calling her.
4. **On a trip, call the other person** (the partner driving her, or the
   rider if a partner pressed it) - only to find out where she is and what is
   happening. **If she has said that person is the danger, do not call them**:
   that is a 112 call.
5. **Call her emergency contacts.** Say she raised an SOS, where she is, and
   that SheOut is on it. Ask whether they can reach her. Partners have no
   contacts on file.
6. **Stay with it.** Keep calling every few minutes until she is safe or the
   police have it. The alert stays open until then.
7. **Close it with what happened.** The console asks for the outcome and a
   note (required for "Something else"):

| Outcome | When |
|---|---|
| False alarm | She was reached, is safe, pressed it by mistake |
| Safe, confirmed | She was reached; something happened; she is safe now |
| Contact confirmed safe | She could not be reached; an emergency contact got to her |
| Police involved | 112 called, or police are with her |
| Something else | Anything else - the note says what |

The outcome and note are stored with the alert (`sos_alert.resolution_outcome`,
`resolution_note`, migration V86) beside who closed it and when. A closed
alert cannot be closed again, so the first record stands.

## Phone numbers in the console

Numbers are masked. **Show** reveals one; that needs the
`pii.phone.reveal` permission (safety responders have it) and is recorded.
Her number, the other person's and her contacts' can be revealed while the
alert is open and for 30 minutes after it closes.

## After

- A **false alarm** needs nothing more.
- Anything else: put the follow-up in the note (who was called, what the
  police said, the complaint number). If she raised an issue from the app, it
  is in **Support** - answer it there. Blocking an account is on the
  person's page, for staff who may block.
