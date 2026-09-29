// Single source of truth for design tokens - imported by tailwind-preset.js
// (so both apps' Tailwind builds share one theme) and re-exported, typed,
// from src/tokens for anywhere JS needs the raw value (the token preview
// page, mostly - components should reach for a Tailwind class, e.g.
// `bg-primary`, not one of these values directly).
//
// SAMPLED FROM THE APPROVED MOCKUP (public/preview.webp), not eyeballed.
// Each value below is the dominant colour of the region of that sheet where
// it appears - see scratchpad sample-colors.js for the extraction. The
// previous values were guesses and were visibly off: primary was #7B3FE4, a
// light violet, where the mockup is a much deeper #4A1A9E, which is why the
// built apps did not read as the same design.

/*
 * COLOUR DEPTH. Every hue has a tint (a wash to sit things on), its base
 * (fills, icons, the thing you notice), and a strong shade (text and icons
 * placed ON the tint, where the base would be too faint to read). Primary
 * also has a mid tone, for the second-most important thing on a purple
 * screen. Use them for hierarchy: the one element that matters most on a
 * screen gets the base or strong shade; everything around it steps down to
 * a tint. Blue is for information - support and announcements - so it never
 * competes with the brand purple for "this is SheOut".
 */
export const colors = {
  primary: '#4A1A9E',
  primaryDark: '#36116F',
  primaryMid: '#7B4FD6',
  primaryLight: '#EDE5FA',
  accentOrange: '#FCA325',
  accentOrangeTint: '#FFF2DE',
  // Darkened from #A15C00, #0B7A3A and #7C7690 (2026-09-28): each fell
  // below WCAG AA's 4.5:1 for body text on at least one of the tinted card
  // backgrounds (grey on the lavender card was 3.5:1). Each now clears
  // 4.5:1 on every light surface and tint: amber 5.1, green 5.1, grey 4.8.
  accentOrangeStrong: '#8F5100',
  accentGreen: '#1AB65A',
  accentGreenTint: '#E2F6EA',
  accentGreenStrong: '#0A7034',
  // Deepened from #FA2A36 (2026-09-28): white on it, and it as text on
  // white, were 3.85:1 - the SOS pill and the unread badge among them.
  accentRed: '#D6131F',
  accentRedTint: '#FFE5E7',
  accentRedStrong: '#B0121C',
  accentBlue: '#2F6FEB',
  accentBlueTint: '#E4EDFF',
  accentBlueStrong: '#1C4BB4',
  background: '#FBFAFD',
  surface: '#FFFFFF',
  border: '#E8E3F1',
  textPrimary: '#241A33',
  textSecondary: '#67617C',
  textInverse: '#FFFFFF',
  // The wordmark's 'OUT' only. A hotter orange than accentOrange, which is
  // the amber used for UI surfaces like the Parcel tile - they are two
  // different colours in the mockup and must not be collapsed into one.
  brandOrange: '#F96717',
};

export const radii = {
  card: '1.25rem',
  input: '0.875rem',
  chip: '0.625rem',
};

/*
 * ELEVATION. Four levels, each for one job - never one shadow for everything:
 *  1 lift     cards and list rows resting on the page
 *  2 float    things that sit above the page: the bottom bar, a primary
 *             call to action, the hero card of a screen (brand-tinted)
 *  3 overlay  dialogs, sheets and menus, which cover the page
 *  pressed    a control being pushed in
 * shadow-card and shadow-raised remain as aliases for lift and float.
 */
export const shadows = {
  lift: '0 1px 2px rgba(36, 26, 51, 0.05), 0 4px 14px rgba(36, 26, 51, 0.06)',
  float: '0 6px 14px rgba(74, 26, 158, 0.14), 0 18px 36px rgba(74, 26, 158, 0.16)',
  overlay: '0 16px 40px rgba(36, 26, 51, 0.20), 0 40px 80px rgba(36, 26, 51, 0.18)',
  pressed: 'inset 0 2px 5px rgba(36, 26, 51, 0.16)',
};

/*
 * TYPE SCALE. Six steps, plus micro for badges, each a size AND a weight AND a line height, so a
 * heading is never "text-sm but bold". Poppins for the first four (read as
 * headings), Inter for body and caption.
 */
export const typeScale = {
  display: ['1.875rem', { lineHeight: '2.25rem', fontWeight: '700', letterSpacing: '-0.02em' }],
  title: ['1.375rem', { lineHeight: '1.75rem', fontWeight: '600', letterSpacing: '-0.01em' }],
  section: ['1.0625rem', { lineHeight: '1.5rem', fontWeight: '600' }],
  'card-title': ['0.9375rem', { lineHeight: '1.25rem', fontWeight: '600' }],
  body: ['0.9375rem', { lineHeight: '1.375rem', fontWeight: '400' }],
  caption: ['0.75rem', { lineHeight: '1rem', fontWeight: '500', letterSpacing: '0.01em' }],
  // Badge numerals and one-word tags only (an unread count, "Soon"). Never
  // for a sentence: anything a person reads is caption or larger.
  micro: ['0.625rem', { lineHeight: '0.75rem', fontWeight: '700', letterSpacing: '0.02em' }],
};

export const spacing = {
  screen: '1.25rem',
};

export const fonts = {
  // Noto after the brand faces: Poppins and Inter have no Telugu or Devanagari,
  // so those scripts fall through to Noto rather than to whatever the phone has.
  heading: ['Poppins', 'Noto Sans Telugu', 'Noto Sans Devanagari', 'sans-serif'],
  body: ['Inter', 'Noto Sans Telugu', 'Noto Sans Devanagari', 'sans-serif'],
};

/**
 * The same roles, for a dark screen.
 * <p>
 * NOT THE LIGHT PALETTE INVERTED. A deep aubergine ground rather than black,
 * because pure black against a bright phone screen at night is the thing
 * that makes text shimmer; and the brand purple lightens to a lavender,
 * because #4A1A9E on a dark ground is a smudge rather than a colour.
 * <p>
 * textInverse flips to near-black, which is the piece that is easy to get
 * wrong: a filled button here is lavender, green or amber, and the label on
 * top of it has to be ink to be readable. That one token keeps every filled
 * control legible without a dark variant on each of them.
 */
export const darkColors = {
  primary: '#B69BFF',
  primaryDark: '#9B79FF',
  primaryMid: '#8F6BF0',
  primaryLight: '#2A2142',
  accentOrange: '#FFB545',
  accentOrangeTint: '#3A2A14',
  accentOrangeStrong: '#FFC56E',
  accentGreen: '#35D07F',
  accentGreenTint: '#16301F',
  accentGreenStrong: '#6BE2A3',
  accentRed: '#FF6B72',
  accentRedTint: '#3A1A21',
  accentRedStrong: '#FF9BA0',
  accentBlue: '#6E9BFF',
  accentBlueTint: '#1A2442',
  accentBlueStrong: '#A3C0FF',
  background: '#131020',
  surface: '#1B1730',
  border: '#2E2847',
  textPrimary: '#F2EFF9',
  textSecondary: '#A79FC0',
  textInverse: '#18122B',
  brandOrange: '#FF8A4C',
};

export const darkShadows = {
  lift: '0 1px 2px rgba(0, 0, 0, 0.40), 0 4px 14px rgba(0, 0, 0, 0.30)',
  float: '0 6px 14px rgba(0, 0, 0, 0.45), 0 18px 36px rgba(0, 0, 0, 0.45)',
  overlay: '0 16px 40px rgba(0, 0, 0, 0.60), 0 40px 80px rgba(0, 0, 0, 0.55)',
  pressed: 'inset 0 2px 5px rgba(0, 0, 0, 0.50)',
};

/** "#4A1A9E" -> "74 26 158", the form a CSS variable needs to keep /opacity working. */
export function rgbChannels(hex) {
  const value = hex.replace('#', '');
  const full = value.length === 3 ? value.split('').map((c) => c + c).join('') : value;
  const number = parseInt(full, 16);
  return [(number >> 16) & 255, (number >> 8) & 255, number & 255].join(' ');
}
