# Third-party UI libraries: licence record

Recorded for the client handover. It covers the two animation libraries
added to the design system in 2026-09. Everything else comes in through
the normal package.json dependencies.

Both are imported in exactly one file each, in the design system:

- `frontend/design-system/src/components/AssistantAvatar.tsx` for `bot-avatars`
- `frontend/design-system/src/components/ThinkingIndicator.tsx` for `thinking-orbs`

Replacing either one is a change to that one file.

## bot-avatars

| | |
|---|---|
| Version | 0.1.1, pinned exactly with no caret |
| Licence | MIT (`license` field in package.json, and a full MIT `LICENSE` file in the published package) |
| Copyright | (c) 2026 Jakub Antalik |
| Maintainer on npm | jakubkubo (jakubja@gmail.com) |
| Repository | https://github.com/Jakubantalik/Libraries.dev |
| Homepage | https://libraries.dev/bots |
| Published | 0.1.0 on 2026-09-22; 0.1.1 on 2026-09-22 (latest) |
| Runtime dependencies | none; peer dependency react >= 18 |
| Install scripts | none (no `preinstall`, `install` or `postinstall`; `prepublishOnly` runs only on the author's machine) |
| npm audit | 0 vulnerabilities (2026-09-28) |
| Licence key or subscription | none. The published code has no key check, no network calls and no Pro gating (checked by reading `dist/`) |

## thinking-orbs

| | |
|---|---|
| Version | 0.3.2, pinned exactly with no caret |
| Licence | MIT (`license` field in package.json, and a full MIT `LICENSE` file in the published package) |
| Copyright | (c) 2026 Jakub Antalik |
| Maintainer on npm | jakubkubo (jakubja@gmail.com) |
| Repository | https://github.com/Jakubantalik/Libraries.dev |
| Homepage | https://libraries.dev/orbs |
| Published | 0.3.0 on 2026-08-11; 0.3.2 on 2026-09-22 (latest) |
| Runtime dependencies | none; peer dependency react >= 18 |
| Install scripts | none |
| npm audit | 0 vulnerabilities (2026-09-28) |
| Licence key or subscription | none; same checks as above |

## The libraries.dev Terms (https://libraries.dev/terms.html, "Last updated September 2026")

- The npm libraries are MIT: "Install them, modify them, and ship them in
  unlimited personal and commercial projects."
- The only restriction is on the paid **Pro** presets and recipes, which
  may not be redistributed as a competing library or kit. SheOut uses no
  Pro content. Both packages are the free npm releases, and nothing Pro is
  added in the SheOut code.
- The software is provided "as is", with no warranty.

**One discrepancy, for the record.** The Terms page names the MIT libraries
as "Beam, Orb, Gooey, Metal and Image" and does not name Bots
(`bot-avatars`). The package that SheOut installs carries its own MIT
licence file and an MIT `license` field. That is the licence under which
this release was published, and it grants commercial use without a key.
The omission is most likely because bot-avatars was published on
2026-09-22, after that list was written. If the client wants certainty,
ask the author to add Bots to the list. Nothing in SheOut depends on the
answer, because the MIT grant for 0.1.1 cannot be withdrawn.

## Obligations

MIT requires the copyright notice and permission notice to be included
with copies of the software. Both notices are in each package's `LICENSE`
file, which is kept in `node_modules`. Vite's production bundle does not
copy licence files, so the notices are reproduced here. This file ships
with the source handover.
