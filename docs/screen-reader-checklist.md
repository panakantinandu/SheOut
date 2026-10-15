# Screen-reader checklist

A script for a **person** to run with a real screen reader. The automated
tests (`frontend/*/e2e/keyboard-*.spec.ts`, `a11y.spec.ts`) check focus,
keyboard reach and what the live regions *contain*; they cannot check what a
screen reader actually *says*, how it pronounces Hindi and Telugu, or whether
it all makes sense to someone who cannot see the screen. **No screen-reader
pass has been done yet.** Record each run at the bottom.

Use test accounts on **staging**, never production, and never a real
customer's or partner's number.

## Set-up

| Reader | Device | Browser | Start / stop | Next item | Activate |
|---|---|---|---|---|---|
| NVDA (free) | Windows laptop | Chrome | Ctrl+Alt+N / NVDA+Q | Tab, or Down arrow in browse mode | Enter / Space |
| TalkBack | Android phone | Chrome | Hold both volume keys 3 s | Swipe right | Double-tap |
| VoiceOver | iPhone | Safari | Side button 3 times (set in Accessibility Shortcut) | Swipe right | Double-tap |

Run every flow in **English**, then repeat the starred (*) steps in
**Hindi** and **Telugu** (Settings, Language). Turn on the phone's large text
(200%) for one full run.

For each step, write **Pass**, or what was said instead.

## Customer app

### 1. Sign in *
1. Open the app. The page title and "Welcome Back" are read.
2. Move to the phone field: read as "Mobile number, edit text" (or similar), not just "edit".
3. Type a number. Move to the Terms and Privacy links: each is read as a button with its name.
4. "Send OTP": read as a button. Activate it.
5. The code field: read as a one-time code field. The phone may offer the code from SMS.
6. A wrong code: the error is read out without moving to it.

### 2. Book a ride *
1. Open Book a ride. "Pickup: <place>" and "Drop: Choose drop point" are buttons with their names.
2. Activate Drop. The address picker opens and is read as a dialog with its title; focus is in the search field.
3. Type a place. Suggestions are reachable and read by name.
4. Swipe or Tab past the last suggestion: you stay inside the picker (it does not read the screen behind it).
5. Pick a suggestion. Focus returns to the Drop field, now read with the new place.
6. The fare is read with the rupee amount. "Book Now" is a button.
7. Book. **"Searching for a nearby partner"** is announced without you moving.

### 3. Follow the trip
1. When a partner accepts: **"Arriving in about N min"** (or "Your partner is on her way") is announced.
2. When the trip starts: **"On the way to your drop"** is announced.
3. The map is not needed: pickup, drop, the partner's name, vehicle and ETA are all readable as text.
4. "Cancel ride": opens a dialog read with its name; you cannot leave it by swiping; Back or Escape closes it and returns you to "Cancel ride".

### 4. SOS *
1. Open SOS. The SOS button is read with its name.
2. Activate it. The countdown dialog is read, **focus is on Cancel**, and each second is announced.
3. Back / Escape cancels; focus returns to the SOS button; **nothing is sent**.
4. Activate again and choose "Send now": a confirmation is read.
5. The English line under the Hindi or Telugu safety text is read too, in English.

### 5. Send a parcel *
1. Choose the drop as in 2. "Continue" opens the parcel details, read as a dialog.
2. Receiver name and phone fields are read with their labels.
3. The two confirmation boxes are read as checkboxes with their full sentences, and their state (checked or not).
4. The book button is only offered once both are ticked.

## Partner app

### 6. Go online *
1. Home: the status card's title and "Go Online" are read.
2. Activate Go Online. **"You're online. Looking for ride requests nearby"** is announced, and focus is on "Go Offline" (not lost to the top of the page).

### 7. An offer
1. When a request arrives: **"New request. You earn ₹N. Pickup: <place>. 15 seconds to respond."** is announced at once.
2. Focus is on the offer details, **not on Accept**.
3. Accept and Decline are buttons with their names.

### 8. The trip
1. **"Step 1 of 2 · Pickup. Go to pickup"** is announced.
2. At the pickup: **"You're at the pickup"** is announced; "Yes, it's her" / "No, it's someone else" are buttons.
3. The pickup-code field is read with its label; "Confirm pickup & start trip" is a button.
4. **"Step 2 of 2 · Drop"** is announced; at the drop **"You've reached the drop"**.
5. "End trip and request payment" is a button.

## Staff console (NVDA, desktop)

1. Sign-in page: heading "Sign in"; email, password and code fields read with labels.
2. Court-record checks (if switched on): the check opens as a dialog; Escape closes it.

## Results

| Date | Who | Reader / device | Language | Flows | Problems found |
|---|---|---|---|---|---|
| | | | | | |
