# SheOut design system

The rules both apps follow. Values live in `tokens.js` (the single source), are
turned into CSS variables for light and dark in `src/theme.css`, and reach
components as Tailwind classes through `tailwind-preset.js`. Use the classes,
never raw hex, pixel or shadow values.

## Colour depth

Every hue has a **tint** (a wash to put things on), its **base** (fills, the
thing you notice) and a **strong** shade (text and icons placed on the tint).
Primary also has a **mid** tone.

| Hue | Tint | Base | Strong | Used for |
|---|---|---|---|---|
| Primary purple | `primary-light` | `primary` | `primary-dark` (`primary-mid` between) | the brand, bookings, the main action |
| Orange | `accent-orange-tint` | `accent-orange` | `accent-orange-strong` | waiting, account, "coming soon" |
| Green | `accent-green-tint` | `accent-green` | `accent-green-strong` | money in, done, safe |
| Red | `accent-red-tint` | `accent-red` / `danger` | `accent-red-strong` | SOS, safety, destructive |
| Blue | `accent-blue-tint` | `accent-blue` | `accent-blue-strong` | information: support, announcements |

Hierarchy: the one element that matters most on a screen gets the base or strong
shade; what surrounds it steps down to a tint. An icon on a tint uses the strong
shade, never the base (base-on-tint is too faint to read, especially orange).

## Elevation

Four levels, each for one job. Never one shadow for everything.

| Class | Level | For |
|---|---|---|
| `shadow-lift` | 1 | cards and list rows resting on the page |
| `shadow-float` | 2 | what sits above the page: the bottom bar, the primary button, a screen's hero card |
| `shadow-overlay` | 3 | dialogs, sheets, the drawer |
| `shadow-pressed` | pressed | a control being pushed (buttons apply it on `:active`) |

`shadow-card` and `shadow-raised` remain as aliases for lift and float.

## Type scale

Each step is a size, weight and line height together. Pair the first four with
`font-heading` (Poppins); body and caption are Inter.

| Class | Size / line | Weight | For |
|---|---|---|---|
| `text-display` | 30 / 36 | 700 | a screen's hero line |
| `text-title` | 22 / 28 | 600 | page titles |
| `text-section` | 17 / 24 | 600 | section headers, the top bar |
| `text-card-title` | 15 / 20 | 600 | card and list-row titles |
| `text-body` | 15 / 22 | 400 | reading text |
| `text-caption` | 12 / 16 | 500 | meta: times, hints, labels |
| `text-micro` | 10 / 12 | 700 | badge numerals and one-word tags only |

Nothing a person reads is smaller than caption.

## Spacing

Multiples of 4 px: Tailwind's whole steps (`1` = 4 px, `2` = 8 px, `3` = 12 px,
`4` = 16 px, `5` = 20 px, `6` = 24 px, `8` = 32 px). No half steps (`gap-1.5`,
`mt-0.5`) and no arbitrary pixel values. Screen gutters are `px-screen` (20 px).
A `Card` pads itself (`p-5`) unless given its own padding class.

## Artwork

SheOut's own illustrations stand for its services and categories, never a
generic icon: `ServiceArt kind="ride" | "parcel"` for the scooter and parcel
tiles, `bikeTaxiArt` / `parcelArt` / `womenArt` for larger uses, and the six
Seller category illustrations in the rider app. Sources are the full-size PNGs
in the repository's `public/` folder; what ships is resized WebP. Lucide icons
are for UI actions (back, menu, close), in the colour of their context.
