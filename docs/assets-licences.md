# Assets and their licences

Every image, illustration, icon, font, video and model the apps and the public
site ship, where it came from, and the terms it is used under. Libraries are
recorded in `docs/THIRD_PARTY_LICENSES.md` and `package.json`.

**Rule:** nothing ships without a row here. An asset whose source is not
known is replaced or removed, not kept "until someone checks". Never present
a photo of a real person as a SheOut customer or partner, and never use
another brand's logo or a look-alike mark.

Last reviewed: 2026-10-14 (branch `feat/legal-privacy-a11y`).

## Shipped in the apps

| File | What | Source | Licence / terms | Attribution |
|---|---|---|---|---|
| `frontend/design-system/src/assets/art/{bike-taxi,parcel,marketplace,women}.webp` | Ride, parcel and marketplace tiles; three women | **The founder's own images**, used since before 2026-10-14. Replaced on 2026-10-14 by drawn SVGs and **restored on 2026-10-15 at the founder's direction** | The founder's. Where they came from and that SheOut may use them commercially is for the founder to record (open item O-11) | None |
| `frontend/customer-app/src/assets/seller/*.webp` (8) | Marketplace category pictures | Same: **the founder's own images**, restored on 2026-10-15 at the founder's direction | Same (O-11) | None |
| Icons throughout (`lucide-react`) | UI icons | lucide.dev | ISC | The ISC notice ships in `node_modules/lucide-react/LICENSE`; no visible credit required |
| `frontend/*/public/icons/*`, `Logo.jpeg`, `public/SheOut-Workspace-*.png` | SheOut logo and app icons | SheOut | Owned by SheOut. **Confirm who drew the logo** and that the rights were assigned to the company (open item O-11) | None |
| `frontend/driver-app/public/models/face/*` | Face-detection models | `@vladmandic/face-api` | MIT | In the package |
| Fonts: Inter, Poppins, Noto Sans Devanagari, Noto Sans Telugu | Typefaces | Google Fonts | SIL Open Font License 1.1 | None required. Loaded from Google today; self-hosting is allowed by the licence (open item O-16) |
| `bot-avatars`, `thinking-orbs` | Assistant avatar and thinking animation | npm | MIT | `docs/THIRD_PARTY_LICENSES.md` |
| Map tiles and Places results | Google Maps Platform | Google | Google Maps Platform Terms | Google's own logo and "Map data" credit stay visible on every map; the code does not hide them |
| `frontend/customer-app/public/videos/sell-on-sheout.*` | "How to sell" video | Made by SheOut from its own app screens | Owned by SheOut | Check the frames for the replaced art (below) |

## Restored on 2026-10-15

On 2026-10-14 the four illustrations and the eight category pictures were
replaced by drawn SVGs, because their source had not been recorded. The founder
said these are SheOut's own images and asked for them back, so the originals
were restored unchanged from `main` and the SVGs removed.

## Still to resolve

| File | Issue | Who |
|---|---|---|
| `frontend/marketing-site/public/screens/*.webp` (17), `public/media/hero-*`, `public/media/sell-on-sheout*`, `public/og-image.jpg` | Real SheOut screens, but recorded with sample data (names, trips, a 4.9 rating) **and they still show the removed illustrations** (scooter, parcel and marketplace tiles; avatars cropped from the old `women.webp`). Every one is labelled "Sample screens" on the site, and the share image has the label burned in | Re-shoot from the current apps with the new art and no rating; then this row goes. Until then the label stays |
| `backend/src/main/resources/sample-shops/*.jpg` (26) | Product photos for the labelled sample shops. Source not recorded. **Kept, as the founder asked** (sample shops stay until launch) | Founder: say where they came from, or replace them before `MARKETPLACE_SAMPLE_SHOPS` stays on in front of real customers |
| Voiced explainer videos (not shipped) | Narration made with edge-tts (Microsoft voices through an unofficial client); commercial use is not clearly allowed | Use a licensed TTS service or a recorded voice before publishing |
