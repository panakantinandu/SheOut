# Assets and their licences

Every image, illustration, icon, font, video and model the apps and the public
site ship, where it came from, and the terms it is used under. Libraries are
recorded in `docs/THIRD_PARTY_LICENSES.md` and `package.json`.

**Rule:** nothing ships without a row here. An asset whose source is not
known is replaced or removed, not kept "until someone checks". Never present
a photo of a real person as a SheOut customer or partner, and never use
another brand's logo or a look-alike mark.

Last reviewed: 2026-10-15 (branch `chore/marketing-assets-refresh`).

## Shipped in the apps

| File | What | Source | Licence / terms | Attribution |
|---|---|---|---|---|
| `frontend/design-system/src/assets/art/bike-taxi.svg` | Ride tile: woman on a scooter, pin | Drawn for SheOut, 2026-10-14 | Owned by SheOut | None |
| `frontend/design-system/src/assets/art/parcel.svg` | Parcel tile | Drawn for SheOut, 2026-10-14 | Owned by SheOut | None |
| `frontend/design-system/src/assets/art/marketplace.svg` | Marketplace tile: shop, bag | Drawn for SheOut, 2026-10-14 | Owned by SheOut | None |
| `frontend/design-system/src/assets/art/women.svg` | Three women, stylised (no real person) | Drawn for SheOut, 2026-10-14 | Owned by SheOut | None |
| `frontend/customer-app/src/assets/seller/*.svg` (8) | Marketplace category tiles | Generated for SheOut, 2026-10-14, by `make-category-art.cjs` (QA scratch): a gradient tile with one Lucide glyph | Tile: owned by SheOut. Glyph: Lucide, ISC | Lucide's ISC notice ships in `node_modules/lucide-react/LICENSE`; no visible credit required |
| Icons throughout (`lucide-react`) | UI icons | lucide.dev | ISC | As above |
| `frontend/*/public/icons/*`, `Logo.jpeg`, `public/SheOut-Workspace-*.png` | SheOut logo and app icons | SheOut | Owned by SheOut. **Confirm who drew the logo** and that the rights were assigned to the company (open item O-11) | None |
| `frontend/driver-app/public/models/face/*` | Face-detection models | `@vladmandic/face-api` | MIT | In the package |
| Fonts: Inter, Poppins, Noto Sans Devanagari, Noto Sans Telugu | Typefaces | Google Fonts | SIL Open Font License 1.1 | None required. Loaded from Google today; self-hosting is allowed by the licence (open item O-16) |
| `bot-avatars`, `thinking-orbs` | Assistant avatar and thinking animation | npm | MIT | `docs/THIRD_PARTY_LICENSES.md` |
| Map tiles and Places results | Google Maps Platform | Google | Google Maps Platform Terms | Google's own logo and "Map data" credit stay visible on every map; the code does not hide them |
| `frontend/customer-app/public/videos/sell-on-sheout.{en,hi,te}.mp4` and posters, `frontend/marketing-site/public/media/sell-on-sheout*` | "How to sell" walkthrough, one per language | Made by SheOut on 2026-10-15 from its own current app screens with sample data (`seller-shots.mjs`, `seller-video.mjs` in `D:_qa-scratchmarketing-refresh`); the sample product photo is SheOut's own Fashion & Saree category tile | Owned by SheOut. Silent, no voice. "Sample screens" burned in | None |
| `frontend/marketing-site/public/screens/*.webp`, `media/hero-*`, `og-image.jpg` | The site's app screenshots, hero loop and share image | Captured by SheOut from its own apps with sample data; per-file record in `frontend/marketing-site/screens.json`, method in `docs/marketing-assets.md` | Owned by SheOut. Map areas are Google's, with Google's logo and credit left visible | "Sample screens" label on the site and burned into the share image |
| Profile photos in the screenshots | Two faces | Cropped from `women.svg` (above), 2026-10-15 | Owned by SheOut | None |

## Removed on 2026-10-14 (source never recorded)

| File | What it was | Why it went |
|---|---|---|
| `design-system/src/assets/art/{bike-taxi,parcel,marketplace,women}.webp` | 3D-style illustrations, likely AI-generated | Source and licence never recorded. Replaced by the SVGs above |
| `customer-app/src/assets/seller/*.webp` (8) | Photo-style category pictures, several showing people | Same. A realistic picture of a person must not read as a real seller. Replaced by the category tiles above |

## Still to resolve

| File | Issue | Who |
|---|---|---|
| Six map screens in `frontend/marketing-site/public/screens/` (book, on the way, arrived, on trip, payment, new request) | Still the 2026-10-06 captures: an invented 4.9 (128) rating on three, the removed scooter art and avatars, paise on one. Re-shooting them needs the Google Maps key at build time. The share image, the hero loop and the three other screens were re-shot on 2026-10-15; eight unused screens were removed | Whoever holds the Maps key runs the steps in `docs/marketing-assets.md`. `check-marketing-assets.mjs --strict` (launch gate) fails until done |
| `backend/src/main/resources/sample-shops/*.jpg` (26) | Product photos for the labelled sample shops. Source not recorded. **Kept, as the founder asked** (sample shops stay until launch) | Founder: say where they came from, or replace them before `MARKETPLACE_SAMPLE_SHOPS` stays on in front of real customers |
| Voiced explainer videos (not shipped) | Narration made with edge-tts (Microsoft voices through an unofficial client); commercial use is not clearly allowed | Use a licensed TTS service or a recorded voice before publishing |
