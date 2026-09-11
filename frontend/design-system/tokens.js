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
  heading: ['Poppins', 'sans-serif'],
  body: ['Inter', 'sans-serif'],
};
