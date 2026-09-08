// Typed re-export of tokens.js (see that file for the "estimated from the
// image" disclaimer). Prefer the matching Tailwind class in components -
// this is for the token preview page and any rare case that genuinely
// needs the raw value in JS.
import { colors, radii, shadows, spacing, fonts } from '../../tokens.js';

export const tokens = { colors, radii, shadows, spacing, fonts } as const;

export type ColorToken = keyof typeof colors;
export type RadiusToken = keyof typeof radii;
export type ShadowToken = keyof typeof shadows;
