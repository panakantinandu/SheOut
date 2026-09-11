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
    },
  },
};
