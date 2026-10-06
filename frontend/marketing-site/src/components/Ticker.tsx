import { Sparkle } from 'lucide-react';
import { TICKER } from '../content';

/** A slow band of what SheOut is, under the hero. Read once by screen readers, not twice. */
export function Ticker() {
  const row = (hidden: boolean) => (
    <ul className="flex shrink-0 items-center" aria-hidden={hidden || undefined}>
      {TICKER.map((t) => (
        <li key={t} className="flex items-center gap-6 px-6 font-heading text-lg font-semibold text-white sm:text-xl">
          {t}
          <Sparkle className="h-4 w-4 text-accent-orange" aria-hidden />
        </li>
      ))}
    </ul>
  );
  return (
    <div className="relative overflow-hidden bg-primary py-5">
      <div className="flex w-max motion-safe:animate-marquee hover:[animation-play-state:paused]">
        {row(false)}
        {row(true)}
      </div>
      <div className="pointer-events-none absolute inset-y-0 left-0 w-16 bg-gradient-to-r from-primary to-transparent" />
      <div className="pointer-events-none absolute inset-y-0 right-0 w-16 bg-gradient-to-l from-primary to-transparent" />
    </div>
  );
}
