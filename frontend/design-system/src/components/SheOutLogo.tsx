import { LOGO_BOUNDS, LOGO_HE, LOGO_PIN, LOGO_RING, LOGO_S, LOGO_UT } from '../brand/logoPaths';
import { cn } from '../lib/cn';

/** SheOut orange, sampled from the logo sheet, and the pale ring under the pin. */
export const BRAND_ORANGE = '#FF6B1A';
const RING_TINT = '#FFC4A3';
const NIGHT = '#1C0F2E';

export type LogoTone =
  /** Orange letters, pale ring - on light or dark screens. */
  | 'brand'
  /** All white - on orange, purple or a photo. */
  | 'white'
  /** The surrounding text colour - for monochrome places. */
  | 'current';

function colours(tone: LogoTone): { ink: string; ring: string; ringOpacity: number } {
  if (tone === 'white') return { ink: '#fff', ring: '#fff', ringOpacity: 0.5 };
  if (tone === 'current') return { ink: 'currentColor', ring: 'currentColor', ringOpacity: 0.45 };
  return { ink: BRAND_ORANGE, ring: RING_TINT, ringOpacity: 1 };
}

/** A viewBox around the given parts of the logo sheet, with a little air. */
function box(parts: (keyof typeof LOGO_BOUNDS)[], pad = 4): string {
  const xs = parts.flatMap((p) => [LOGO_BOUNDS[p][0], LOGO_BOUNDS[p][2]]);
  const ys = parts.flatMap((p) => [LOGO_BOUNDS[p][1], LOGO_BOUNDS[p][3]]);
  const x0 = Math.min(...xs) - pad;
  const y0 = Math.min(...ys) - pad;
  return `${x0} ${y0} ${Math.max(...xs) - x0 + pad} ${Math.max(...ys) - y0 + pad}`;
}

/**
 * The SheOut wordmark - "SheOut" with the S drawn as a woman's profile and
 * the O as a map pin on its ground ring - traced from the brand's own logo
 * sheet (public/SheOut-Final-Logo.png), so it is the logo, not a likeness.
 * Sized by its width: set a width class and the height follows.
 */
export function SheOutWordmark({ tone = 'brand', className, title = 'SheOut' }: { tone?: LogoTone; className?: string; title?: string }) {
  const c = colours(tone);
  return (
    <svg viewBox={box(['s', 'he', 'pin', 'ring', 'ut'])} className={cn('block h-auto', className)} role="img" aria-label={title}>
      <path fill={c.ring} fillOpacity={c.ringOpacity} fillRule="evenodd" d={LOGO_RING} />
      <path fill={c.ink} fillRule="evenodd" d={LOGO_S} />
      <path fill={c.ink} fillRule="evenodd" d={LOGO_HE} />
      <path fill={c.ink} fillRule="evenodd" d={LOGO_PIN} />
      <path fill={c.ink} fillRule="evenodd" d={LOGO_UT} />
    </svg>
  );
}

/** The S alone - the woman's profile - for small places where the whole word will not fit. */
export function SheOutMark({ tone = 'brand', className, title }: { tone?: LogoTone; className?: string; title?: string }) {
  const c = colours(tone);
  return (
    <svg viewBox={box(['s'])} className={cn('block', className)} role={title ? 'img' : undefined}
      aria-label={title} aria-hidden={title ? undefined : true}>
      <path fill={c.ink} fillRule="evenodd" d={LOGO_S} />
    </svg>
  );
}

/**
 * The app icon as a tile, drawn rather than loaded: the rider app's is the
 * white S on SheOut orange, the partner app's the orange S on night - the
 * same family, told apart the way a driver app is from its rider app.
 */
export function SheOutAppIcon({ app, className }: { app: 'rider' | 'partner'; className?: string }) {
  const rider = app === 'rider';
  return (
    <span
      className={cn('flex items-center justify-center overflow-hidden rounded-[22%]', className)}
      style={{ background: rider ? BRAND_ORANGE : NIGHT }}
      aria-hidden="true"
    >
      <SheOutMark tone={rider ? 'white' : 'brand'} className="h-[66%] w-auto" />
    </span>
  );
}
