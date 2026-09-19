import type { ReactNode } from 'react';
import { BrandHeader } from './BrandHeader';

export interface BrandSplashItem {
  key: string;
  label: string;
  icon: ReactNode;
}

export interface BrandSplashProps {
  /** The line between the orange rules under the wordmark. */
  tagline?: string;
  /** Shown under the wordmark, e.g. "Partner app". */
  badge?: string;
  /** White glyphs across the wave - the services, or what a partner does. */
  items: BrandSplashItem[];
  /** The small caps line at the very bottom. */
  footerLine: string;
  /** Fades the whole screen out as it hands over to the next one. */
  fading: boolean;
}

/**
 * The first screen of both apps: the illustration and wordmark over a warm
 * wash, a skyline, and the purple-and-orange wave.
 * <p>
 * Shared so the partner app opens on the same brand as the rider app - it
 * used to open on a logo JPEG on a plain page, which read as a different,
 * unfinished product.
 * <p>
 * FLAGGED - APPROXIMATED, NOT MEASURED: the wave's curve and the skyline's
 * building sizes are drawn to read as "an organic wave" and "a faint
 * skyline", not traced from the mockup.
 */
export function BrandSplash({ tagline, badge, items, footerLine, fading }: BrandSplashProps) {
  return (
    <div
      // Warm pink-to-lavender wash, sampled from the mockup's splash tile.
      className={`relative min-h-screen overflow-hidden bg-gradient-to-br from-[#FEF8F8] via-[#FBF1F6] to-[#E9DEF5] transition-opacity duration-300 ${
        fading ? 'opacity-0' : 'opacity-100'
      }`}
      data-testid="splash"
    >
      <svg
        viewBox="0 0 400 100"
        preserveAspectRatio="none"
        className="absolute inset-x-0 bottom-[30%] h-24 w-full text-primary/10"
        aria-hidden="true"
      >
        <rect x="-10" y="45" width="34" height="55" fill="currentColor" />
        <rect x="26" y="20" width="26" height="80" fill="currentColor" />
        <rect x="56" y="55" width="22" height="45" fill="currentColor" />
        <rect x="82" y="35" width="30" height="65" fill="currentColor" />
        <rect x="116" y="60" width="20" height="40" fill="currentColor" />
        <rect x="140" y="15" width="28" height="85" fill="currentColor" />
        <rect x="172" y="48" width="24" height="52" fill="currentColor" />
        <rect x="200" y="30" width="32" height="70" fill="currentColor" />
        <rect x="236" y="58" width="20" height="42" fill="currentColor" />
        <rect x="260" y="22" width="26" height="78" fill="currentColor" />
        <rect x="290" y="50" width="24" height="50" fill="currentColor" />
        <rect x="318" y="38" width="30" height="62" fill="currentColor" />
        <rect x="352" y="58" width="22" height="42" fill="currentColor" />
        <rect x="380" y="25" width="30" height="75" fill="currentColor" />
      </svg>

      <svg
        viewBox="0 0 400 300"
        preserveAspectRatio="none"
        className="absolute inset-x-0 bottom-0 h-[38%] w-full"
        aria-hidden="true"
      >
        <defs>
          <linearGradient id="splashWave" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#4A1A9E" />
            <stop offset="100%" stopColor="#36116F" />
          </linearGradient>
        </defs>
        <path d="M0,52 C70,-3 150,82 230,37 C300,0 350,57 400,27 L400,300 L0,300 Z" fill="#FBA226" />
        <path d="M0,70 C70,15 150,100 230,55 C300,18 350,75 400,45 L400,300 L0,300 Z" fill="url(#splashWave)" />
      </svg>

      <div className="relative z-10 flex min-h-screen flex-col items-center px-screen py-10">
        <div className="flex flex-1 flex-col items-center justify-center gap-3 pb-12">
          <BrandHeader size="lg" tagline={tagline} />
          {badge && (
            <span className="rounded-full bg-primary px-4 py-1 text-xs font-bold uppercase tracking-widest text-text-inverse shadow-card">
              {badge}
            </span>
          )}
        </div>

        <div className="flex w-full flex-col items-center gap-4 pb-2">
          <div className="flex w-full items-stretch justify-center">
            {items.map((item, i) => (
              <div
                key={item.key}
                className={`flex flex-1 flex-col items-center gap-2 px-2 ${i > 0 ? 'border-l border-text-inverse/25' : ''}`}
              >
                <span className="text-text-inverse [&>svg]:h-7 [&>svg]:w-7">{item.icon}</span>
                <span className="text-center text-xs font-semibold text-text-inverse">{item.label}</span>
              </div>
            ))}
          </div>
          <p className="text-xs font-medium uppercase tracking-wide text-text-inverse/70">{footerLine}</p>
        </div>
      </div>
    </div>
  );
}
