// Single source of truth for design tokens - imported by tailwind-preset.js
// (so both apps' Tailwind builds share one theme) and re-exported, typed,
// from src/tokens for anywhere JS needs the raw value (the token preview
// page, mostly - components should reach for a Tailwind class, e.g.
// `bg-primary`, not one of these values directly).
//
// ESTIMATED FROM THE MOCKUP IMAGE, NOT READ FROM a source of truth (no
// Figma/spec was provided) - every color, radius, and shadow value below
// is an eyeballed approximation. Treat this whole file as the thing to
// double-check pixel-for-pixel against the original design.

export const colors = {
  primary: '#7B3FE4',
  primaryDark: '#5A2DA0',
  primaryLight: '#EFE6FC',
  accentOrange: '#F5A623',
  accentGreen: '#27AE60',
  accentRed: '#E5484D',
  background: '#F8F6FC',
  surface: '#FFFFFF',
  border: '#E8E3F1',
  textPrimary: '#241A33',
  textSecondary: '#7C7690',
  textInverse: '#FFFFFF',
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
