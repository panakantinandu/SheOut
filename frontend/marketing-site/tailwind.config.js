import preset from '@sheout/design-system/tailwind-preset';

/** @type {import('tailwindcss').Config} */
export default {
  presets: [preset],
  content: [
    './index.html',
    './src/**/*.{ts,tsx}',
    // The wordmark is used straight from the design system's source.
    '../design-system/src/components/SheOutLogo.tsx',
  ],
  theme: {
    extend: {
      maxWidth: { site: '76rem' },
      transitionTimingFunction: {
        // Fast out, gentle settle - for things arriving on screen.
        'out-expo': 'cubic-bezier(0.16, 1, 0.3, 1)',
      },
      keyframes: {
        'word-rise': {
          from: { transform: 'translateY(110%)', opacity: '0' },
          to: { transform: 'translateY(0)', opacity: '1' },
        },
        'sos-wave': {
          '0%': { transform: 'scale(0.6)', opacity: '0.7' },
          '100%': { transform: 'scale(2.4)', opacity: '0' },
        },
        'scroll-cue': {
          '0%': { transform: 'translateY(0)', opacity: '0' },
          '30%': { opacity: '1' },
          '100%': { transform: 'translateY(14px)', opacity: '0' },
        },
      },
      animation: {
        'word-rise': 'word-rise 0.9s cubic-bezier(0.16, 1, 0.3, 1) both',
        'sos-wave': 'sos-wave 2.4s cubic-bezier(0.16, 1, 0.3, 1) infinite',
        'scroll-cue': 'scroll-cue 1.8s ease-in-out infinite',
        'float-slow': 'float 6s ease-in-out infinite',
      },
    },
  },
};
