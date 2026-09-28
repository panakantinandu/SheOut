# SheOut - how it works (the help assistant's only source)

This is written from how the SheOut apps and backend actually behave. The
rider app's Safety Center says the same things in the same words; if one
changes, change the other (frontend/customer-app/src/i18n/en.safety.json,
keys under `safetyCenter`).

## What SheOut is

SheOut is a ride and delivery app in Hyderabad, India, by women for women:
every rider and every partner (driver) is a woman whose ID has been checked
by a person. Riders book bike taxi, auto, cab, parcel and lunch-box delivery.
Partners drive and are paid into their SheOut wallet.

## Verification - what is checked, and by whom

- Every account signs in with a phone number and a one-time code.
- A rider submits an identity document (with the Aadhaar number masked) and
  takes a live selfie in the app, following on-screen prompts. A person on
  SheOut's operations team compares the selfie with the ID photo and checks
  the document. Nothing is approved automatically - no face-matching software
  decides. A rider cannot book until she is approved.
- A partner does the same, plus her vehicle's registration certificate (RC),
  which must match the registration number on her profile. SheOut's
  operations team also records a police verification for every partner.
  A partner cannot go online and receive trips until both checks pass, and
  she is re-checked as verified each time a trip is offered.
- A rejected document comes back with the reason, and can be sent again.

## During a trip

- Before you get in, read your pickup code out to your partner. She cannot
  start the trip without it, and only the partner assigned to your trip can
  use it.
- You see your partner's name, photo, vehicle and registration number, and
  her position on the map while she comes to you and during the trip.
- In-app chat with your partner. Phone numbers are never shared between
  rider and partner.
- "Share trip" (on the trip screen) opens your phone's share sheet with a
  message: your partner's name and registration, where you are going, and a
  map link to where your partner is at that moment. It is a snapshot, not a
  live-updating link, and SheOut publishes no trip page. Send it to anyone
  you choose; share again for a newer position.
- "Share location" on the SOS screen does the same with your own position.
- If a trip ends well away from your destination, your partner must give a
  reason, and trips whose driven route is far longer than the booked one are
  flagged for a person to review.
- Change of destination mid-trip needs your partner's agreement; the new fare
  is shown before she accepts.

## SOS - step by step

1. Open SOS (the red button in the bottom bar) and press the SOS circle.
2. The app takes your location and sends the alert to SheOut. SheOut texts
   every emergency contact you have saved an SMS with your location, and
   SheOut's operations team is alerted on their devices at the same moment.
   You see how many of your contacts were reached.
3. If SheOut's text could not reach someone, the app offers to send the same
   message from your own phone's SMS app in one tap, and to share your
   location another way.
4. "Call 112" dials the emergency number for where your phone is (112 in
   India).
- Emergency contacts: add them in Profile > Emergency contacts. They are
  only ever messaged when you raise an SOS. Tell them you added them.
- Partners have SOS too; theirs goes to SheOut's operations team.

### Discreet SOS (without pressing the button)

- With discreet SOS turned on (Safety Center or the introduction screens),
  during a trip with the SheOut app open you can either shake your phone
  firmly several times in a row, or knock twice sharply on the back of it.
- The app then shows a 3-second countdown with a large Cancel button, so an
  accidental shake does not alert anyone. If you do nothing, the SOS goes out
  exactly as if you had pressed the button. Doing the gesture again during
  the countdown sends it at once.
- Limit: a web app can only feel these gestures while it is open on screen.
  With the screen locked or another app in front, use your phone's own
  shortcut instead: on iPhone, Settings > Accessibility > Touch > Back Tap
  can open SheOut's SOS link (the Safety Center shows how); some Android
  phones have a similar quick-tap gesture. Opening that link starts the same
  countdown.

### When data is slow or there is no signal

- If SheOut has not confirmed your alert within about 5 seconds, the app
  opens your phone's SMS app with your emergency contacts and your location
  already filled in, while it keeps trying SheOut. You still need to press
  Send - a web app cannot send a text by itself.
- With no signal at all, the app keeps your alert on your phone and sends it
  to SheOut automatically the moment a connection returns - you do not need
  to press anything again. It opens the SMS app at once too, because a text
  can sometimes get through when data cannot.
- Every alert records how it arrived, so SheOut's operations team can see it.

## SheOut SOS or 112?

- Call 112 when you are in danger right now, someone is hurt, or you need
  police, ambulance or fire. 112 is the emergency service; SheOut is not.
- Use SheOut SOS to alert your emergency contacts and SheOut's team with your
  location, for example when you cannot talk, or alongside calling 112.
- You can do both: the SOS screen has a Call 112 button.

## What to do if...

- You feel uncomfortable but it is not an emergency: share your trip with
  someone you trust, message your partner in the app, and afterwards raise a
  support ticket (Help & Support) or rate the trip with a reason. You can ask
  your partner to stop at a safe, busy place and get out there.
- You cannot safely reach your phone: discreet SOS (shake, or a double knock
  on the back) or your phone's own back-tap shortcut starts SOS without
  looking at the screen; the countdown sends it unless you cancel.
- You have no signal: raise SOS anyway. The app opens your SMS app with your
  contacts and location, and sends the alert to SheOut automatically when a
  connection returns. If you can, call 112 - emergency calls often work when
  data does not.
- The trip does not match what was booked (different person, different
  vehicle or registration, wrong route): do not get in if the person or
  vehicle does not match the app, and do not read out your pickup code.
  Cancel and raise a support ticket. If you feel unsafe, use SOS or call 112.

## Payments and wallet

- Riders pay in the app after the trip: from the SheOut wallet or online
  (UPI apps, card, netbanking) through Razorpay. Cash is not accepted, and a
  rider should never pay to a partner's own account. A partner can show a
  SheOut UPI QR for the exact fare at the end of the trip; paying that is the
  same as paying in the app.
- A rider with an unpaid trip cannot book again until it is paid.
- Wallet: add money in Wallet; it is used for fares and can pay the SheOut
  Seller listing fee.
- Refunds, wrong charges and payment problems are handled by a person
  through a support ticket.

## Partners' earnings

- A partner earns the fare minus SheOut's platform fee, shown on each trip.
  Earnings go to her SheOut wallet; she requests a payout to her bank account
  or UPI ID from Payouts, and operations sends it.

## Cancellations

- Riders and partners can cancel with a reason. Repeated cancellations are
  reviewed by a person; nothing is blocked automatically for them.

## Offers, referrals, SheOut Seller

- New riders get signup ride credit while the offer runs. Refer a Friend:
  both get a reward after the friend's first paid trip.
- SheOut Seller is a directory of women selling from home. SheOut charges
  sellers a one-time listing fee and is not part of any sale - buyers
  contact the seller on WhatsApp or by phone.

## Help and support

- Help & Support in the app: FAQs, and "Raise an issue" to open a support
  ticket. A person on SheOut's team reads every ticket and replies in the
  app; it is not instant.
- Account deletion and your data: Profile > Privacy.
