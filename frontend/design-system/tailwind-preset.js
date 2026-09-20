import { radii, spacing, fonts } from './tokens.js';

/**
 * Every colour is a CSS variable, not a hex value, so one stylesheet can
 * repaint the whole app for a dark screen without a second set of classes.
 * The variable holds channels ("74 26 158") rather than a colour, which is
 * what lets Tailwind keep its opacity suffixes working - bg-primary/40 still
 * means what it says. The values themselves live in theme.css, from tokens.js.
 */
const token = (name) => `rgb(var(--c-${name}) / <alpha-value>)`;

/**
 * Shared Tailwind preset - both apps' tailwind.config.js do
 * `presets: [require('@sheout/design-system/tailwind-preset')]` (or the
 * ESM equivalent) so the token set only exists in one place. Extend this,
 * don't fork it - a color/radius/shadow needed in one app is needed in
 * both, since they share the component library that consumes these
 * classes.
 */
export default {
  theme: {
    fontFamily: {
      sans: fonts.body,
      heading: fonts.heading,
    },
    extend: {
      colors: {
        primary: {
          DEFAULT: token('primary'),
          dark: token('primary-dark'),
          light: token('primary-light'),
        },
        accent: {
          orange: token('accent-orange'),
          'brand-orange': token('brand-orange'),
          green: token('accent-green'),
          red: token('accent-red'),
        },
        danger: token('accent-red'),
        background: token('background'),
        surface: token('surface'),
        border: token('border'),
        text: {
          primary: token('text-primary'),
          secondary: token('text-secondary'),
          inverse: token('text-inverse'),
        },
      },
      borderRadius: {
        card: radii.card,
        input: radii.input,
        chip: radii.chip,
      },
      backgroundImage: {
        // Sign-in and the introduction. A gradient rather than a flat colour,
        // and a variable rather than three hexes in a class name, so the
        // dark theme can restate it in one place - see theme.css.
        'brand-wash': 'var(--brand-wash)',
      },

      boxShadow: {
        card: 'var(--shadow-card)',
        raised: 'var(--shadow-raised)',
      },
      spacing: {
        screen: spacing.screen,
      },

      /*
       * The app's whole motion vocabulary, in one place, as CSS. No
       * animation library: every effect here is a handful of keyframes, and
       * a partner on a slow connection should not download a library to get
       * them. Each one is disabled wholesale by the prefers-reduced-motion
       * block in both apps' index.css.
       */
      keyframes: {
        // A loading placeholder, lit by a band of light travelling across it.
        shimmer: {
          '0%': { backgroundPosition: '-150% 0' },
          '100%': { backgroundPosition: '150% 0' },
        },
        // A ring expanding out of a dot and fading: "live", not "urgent".
        'pulse-ring': {
          '0%': { transform: 'scale(1)', opacity: '0.55' },
          '70%': { transform: 'scale(2.6)', opacity: '0' },
          '100%': { transform: 'scale(2.6)', opacity: '0' },
        },
        // What a screen does when it arrives. Deliberately small: six pixels.
        'fade-slide-in': {
          from: { opacity: '0', transform: 'translateY(6px)' },
          to: { opacity: '1', transform: 'none' },
        },
        // A checkmark drawing itself, by retracting the dash that hides it.
        'draw-check': {
          from: { strokeDashoffset: '48' },
          to: { strokeDashoffset: '0' },
        },
        // The tab you have just landed on, springing up once as it takes
        // the pill. Overshoots and settles - a tap should feel answered.
        'nav-pop': {
          '0%': { transform: 'scale(1)' },
          '45%': { transform: 'scale(1.22)' },
          '100%': { transform: 'scale(1)' },
        },
        // The brand mark breathing on the entry screens. Six pixels, four
        // seconds: enough that the screen is alive, little enough that it is
        // never the thing you are looking at.
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-6px)' },
        },
        // Two soft colour washes drifting behind the sign-in card. Transform
        // only, so it costs the compositor and not the CPU on a cheap phone.
        drift: {
          '0%, 100%': { transform: 'translate3d(0, 0, 0) scale(1)' },
          '50%': { transform: 'translate3d(6%, -4%, 0) scale(1.12)' },
        },
        'drift-slow': {
          '0%, 100%': { transform: 'translate3d(0, 0, 0) scale(1.05)' },
          '50%': { transform: 'translate3d(-7%, 5%, 0) scale(0.95)' },
        },
        // The splash's wave settling in from below as the app opens.
        'rise-in': {
          from: { transform: 'translateY(18%)', opacity: '0' },
          to: { transform: 'translateY(0)', opacity: '1' },
        },
        // The splash's progress line, filling over exactly as long as the
        // splash is held - an honest bar, not a spinner that means nothing.
        'fill-bar': {
          from: { transform: 'scaleX(0)' },
          to: { transform: 'scaleX(1)' },
        },
        // The cursor in the empty box of the code field.
        caret: {
          '0%, 45%': { opacity: '1' },
          '55%, 100%': { opacity: '0' },
        },
        'pop-in': {
          from: { transform: 'scale(0.86)', opacity: '0' },
          to: { transform: 'scale(1)', opacity: '1' },
        },
        // The dark layer behind a sheet or dialog, arriving.
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        // A sheet rising from the bottom edge of the screen.
        'sheet-up': {
          from: { transform: 'translateY(12%)', opacity: '0' },
          to: { transform: 'translateY(0)', opacity: '1' },
        },
      },
      animation: {
        shimmer: 'shimmer 1.6s linear infinite',
        'pulse-ring': 'pulse-ring 2.4s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'fade-slide-in': 'fade-slide-in 200ms ease-out both',
        'draw-check': 'draw-check 420ms ease-out 120ms both',
        'pop-in': 'pop-in 220ms ease-out both',
        float: 'float 4s ease-in-out infinite',
        drift: 'drift 22s ease-in-out infinite',
        'drift-slow': 'drift-slow 28s ease-in-out infinite',
        'rise-in': 'rise-in 620ms cubic-bezier(0.22, 1, 0.36, 1) both',
        caret: 'caret 1.1s step-end infinite',
        'nav-pop': 'nav-pop 320ms cubic-bezier(0.34, 1.56, 0.64, 1) both',
        'fade-in': 'fade-in 160ms ease-out both',
        'sheet-up': 'sheet-up 260ms cubic-bezier(0.22, 1, 0.36, 1) both',
      },
    },
  },
};
