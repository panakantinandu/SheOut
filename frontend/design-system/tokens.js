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

export const colors = {
  primary: '#4A1A9E',
  primaryDark: '#36116F',
  primaryLight: '#EDE5FA',
  accentOrange: '#FCA325',
  accentGreen: '#1AB65A',
  accentRed: '#FA2A36',
  background: '#FBFAFD',
  surface: '#FFFFFF',
  border: '#E8E3F1',
  textPrimary: '#241A33',
  textSecondary: '#7C7690',
  textInverse: '#FFFFFF',
  // The wordmark's 'OUT' only. A hotter orange than accentOrange, which is
  // the amber used for UI surfaces like the Parcel tile - they are two
  // different colours in the mockup and must not be collapsed into one.
  brandOrange: '#F96717',
};

export const radii = {
  card: '20px',
  input: '14px',
  chip: '10px',
};

export const shadows = {
  card: '0 4px 16px rgba(36, 26, 51, 0.08)',
  raised: '0 10px 24px rgba(123, 63, 228, 0.30)',
};

export const spacing = {
  screen: '20px',
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
  primaryLight: '#2A2142',
  accentOrange: '#FFB545',
  accentGreen: '#35D07F',
  accentRed: '#FF6B72',
  background: '#131020',
  surface: '#1B1730',
  border: '#2E2847',
  textPrimary: '#F2EFF9',
  textSecondary: '#A79FC0',
  textInverse: '#18122B',
  brandOrange: '#FF8A4C',
};

export const darkShadows = {
  card: '0 4px 16px rgba(0, 0, 0, 0.45)',
  raised: '0 10px 24px rgba(0, 0, 0, 0.55)',
};

/** "#4A1A9E" -> "74 26 158", the form a CSS variable needs to keep /opacity working. */
export function rgbChannels(hex) {
  const value = hex.replace('#', '');
  const full = value.length === 3 ? value.split('').map((c) => c + c).join('') : value;
  const number = parseInt(full, 16);
  return [(number >> 16) & 255, (number >> 8) & 255, number & 255].join(' ');
}
