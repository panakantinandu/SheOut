import { colors, radii, shadows, spacing, fonts } from './tokens.js';

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
          DEFAULT: colors.primary,
          dark: colors.primaryDark,
          light: colors.primaryLight,
        },
        accent: {
          orange: colors.accentOrange,
          'brand-orange': colors.brandOrange,
          green: colors.accentGreen,
          red: colors.accentRed,
        },
        danger: colors.accentRed,
        background: colors.background,
        surface: colors.surface,
        border: colors.border,
        text: {
          primary: colors.textPrimary,
          secondary: colors.textSecondary,
          inverse: colors.textInverse,
        },
      },
      borderRadius: {
        card: radii.card,
        input: radii.input,
        chip: radii.chip,
      },
      boxShadow: {
        card: shadows.card,
        raised: shadows.raised,
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
        'pop-in': {
          from: { transform: 'scale(0.86)', opacity: '0' },
          to: { transform: 'scale(1)', opacity: '1' },
        },
      },
      animation: {
        shimmer: 'shimmer 1.6s linear infinite',
        'pulse-ring': 'pulse-ring 2.4s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'fade-slide-in': 'fade-slide-in 200ms ease-out both',
        'draw-check': 'draw-check 420ms ease-out 120ms both',
        'pop-in': 'pop-in 220ms ease-out both',
        'nav-pop': 'nav-pop 320ms cubic-bezier(0.34, 1.56, 0.64, 1) both',
      },
    },
  },
};
