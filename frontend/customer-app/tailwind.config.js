import preset from '@sheout/design-system/tailwind-preset';

/** @type {import('tailwindcss').Config} */
export default {
  presets: [preset],
  content: [
    './index.html',
    './src/**/*.{ts,tsx}',
    // The design system ships as source, not a pre-built package - Tailwind
    // has to scan it directly here to generate classes used inside its components.
    '../design-system/src/**/*.{ts,tsx}',
  ],
};
