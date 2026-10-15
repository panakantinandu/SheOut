# Marketing screenshots, share image and video

What the public site (`frontend/marketing-site`) shows, how each picture was
made, and how to re-shoot. The per-file record is
`frontend/marketing-site/screens.json`; `scripts/check-marketing-assets.mjs`
checks it in CI.

## Rules

- **Real app screens with sample data**, labelled "Sample screens" on the site
  and burned into the share image. Never a mock-up drawn to look like the app.
- **No rating, review count or user count.** SheOut has not launched; any such
  number is invented. Partners show as "New partner" / "Not rated yet".
- **Whole rupees**, as the apps charge.
- **Only art SheOut owns** (`docs/assets-licences.md`). Profile photos in the
  shots are the original avatars, cropped from the founder's `design-system/src/assets/art/women.webp`.
- No real person's name, number, plate or photo. The sample names (Ananya,
  Lakshmi, Priya), `+91 90000 001xx` numbers and the `TS09EB5678` plate are
  invented test data.

## State on 2026-10-15

| Picture | State |
|---|---|
| 2 screens without a map (partner home, payouts) | Re-shot from the current apps (the marketplace screen stood in for the seller video until it was re-made, then was removed) |
| 4 screens with a map (partner on the way, arrived, on trip, payment) | **Still the 2026-10-06 shots**: an invented 4.9 (128) rating on three, paise on the payment screen. They need the Google map, and the local builds have no Maps key. (Book a ride and New request are 2026-10-06 shots too, with the founder's own art and nothing wrong in them.) |
| 8 screens nothing on the site used (home, safety, SOS, contacts, ID verified, earnings, going to pickup, partner on trip) | Removed from `public/screens`; re-shot copies are in the capture folder if a section wants them |
| Share image (`og-image.jpg`) | Rebuilt from re-shot screens without a map |
| Hero loop (`media/hero-*`) | Re-rendered from re-shot screens without a map. The trip sequence returns when the map screens are re-shot |
| "Sell on SheOut" video | **Re-made on 2026-10-15** from the current app: a scripted walkthrough of the three ways in and the four registration steps, sample data, captions, no voice. English on the site; English, Hindi and Telugu in the customer app (each customer gets her language). Made by `seller-shots.mjs` (screens) and `seller-video.mjs` (video). The Hindi and Telugu captions need a fluent reader |
| Partner payouts screen | Showed `₹3,413.00`: the screen printed two decimals. Fixed in the app on `fix/whole-rupee-money`; the site screenshot was re-shot from that build and reads `₹3,413` |

## How to re-shoot

The tools live outside the repo, in `D:\_qa-scratch\marketing-refresh` (built
on the record/replay harness in `D:\SheOut-Marketing`):

- `shots.mjs replay` opens the **local production builds** of both apps in a
  Pixel 7 browser (412×839 at 3×). The builds are served inside that browser
  under the apps' public addresses, so a Maps key restricted to those
  addresses works. Every API call is answered from answers recorded from a
  local backend (`D:\SheOut-Marketing\fixtures`). Nothing is fetched from
  Vercel and nothing reaches the production backend. It blanks ratings, rounds
  money to whole rupees, and uses the original avatars.
- `video-build/render.mjs loop` renders the 24-second hero loop from those
  shots.
- `publish.mjs` writes the site's screens (618×1259 WebP), the share image and
  the hero loop into `frontend/marketing-site/public`.

**Pending until the owner supplies the Maps key** (decision of 2026-10-15:
the key is never taken from a live site). The owner creates a local file
**outside the repo**, `D:SheOut-Marketingmaps.env`, with two lines copied
from the Vercel project:

```
VITE_GOOGLE_MAPS_API_KEY=...
VITE_GOOGLE_MAPS_MAP_ID=...
```

Then, with `chore/marketing-assets-refresh` checked out:

```
node D:_qa-scratchmarketing-refreshun-with-maps.mjs
```

It builds both apps with the key (passed only to the build, never printed or
written), takes every screenshot, rebuilds both apps **without** the key,
renders the hero loop with the trip sequence, and publishes into the site.
Without the file it stops and changes nothing.

Then look at every shot, remove the `oldCapture` notes from `screens.json`
for the re-shot screens, and run `node scripts/check-marketing-assets.mjs --strict`.

## Still pending

- **The six map screenshots** and the hero loop's trip sequence: run the
  command above once `maps.env` exists.
