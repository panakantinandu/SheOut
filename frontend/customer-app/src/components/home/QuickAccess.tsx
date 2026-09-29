import { Clock, MapPinned, ShieldAlert, Wallet } from 'lucide-react';
import type { ReactNode } from 'react';

export type QuickItem = {
  key: 'sos' | 'live' | 'wallet' | 'history';
  label: string;
  sub: string;
  onOpen: () => void;
};

/** Each shortcut's colour and its one small sign of life. */
const LOOK: Record<QuickItem['key'], { tile: string; icon: string; glyph: ReactNode; live: ReactNode }> = {
  // SOS breathes: a ring going out from it, the same "live" as the rest of the app.
  sos: {
    tile: 'bg-accent-red-tint',
    icon: 'bg-danger text-white',
    glyph: <ShieldAlert />,
    live: <span className="absolute inset-0 -z-10 rounded-full bg-danger/60 motion-safe:animate-pulse-ring" />,
  },
  // Live Track: the pin hovers, with a ping where it points.
  live: {
    tile: 'bg-primary-light',
    icon: 'bg-primary text-white [&>svg]:motion-safe:animate-float',
    glyph: <MapPinned />,
    live: <span className="absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full bg-accent-green ring-2 ring-surface"><span className="absolute inset-0 rounded-full bg-accent-green motion-safe:animate-pulse-ring" /></span>,
  },
  // Wallet: a glint crossing now and then.
  wallet: {
    tile: 'bg-accent-green-tint',
    icon: 'bg-accent-green text-white',
    glyph: <Wallet />,
    live: <span className="pointer-events-none absolute inset-0 overflow-hidden rounded-full"><span className="absolute inset-y-0 left-0 w-1/2 bg-gradient-to-r from-transparent via-white/60 to-transparent motion-safe:animate-sheen" style={{ animationDelay: '1.5s' }} /></span>,
  },
  // History: a glint too, on a later beat than the wallet's.
  history: {
    tile: 'bg-accent-blue-tint',
    icon: 'bg-accent-blue text-white',
    glyph: <Clock />,
    live: <span className="pointer-events-none absolute inset-0 overflow-hidden rounded-full"><span className="absolute inset-y-0 left-0 w-1/2 bg-gradient-to-r from-transparent via-white/60 to-transparent motion-safe:animate-sheen" style={{ animationDelay: '3.2s' }} /></span>,
  },
};

/**
 * Quick Access: SOS, the trip happening now, the wallet and past trips, as
 * four coloured tiles two to a row - each with what it is for in a few
 * words and one small movement of its own, so the row reads at a glance
 * and does not look like four grey circles.
 */
export function QuickAccess({ title, items }: { title: string; items: QuickItem[] }) {
  return (
    <section data-testid="home-quick-access">
      <h2 className="mb-3 font-heading text-section text-text-primary">{title}</h2>
      <div className="grid grid-cols-2 gap-3">
        {items.map((item, i) => {
          const look = LOOK[item.key];
          return (
            <button
              key={item.key}
              type="button"
              onClick={item.onOpen}
              style={{ animationDelay: `${i * 70}ms` }}
              className={`group relative isolate flex min-w-0 items-center gap-2.5 overflow-hidden rounded-[1.25rem] ${look.tile} p-3 text-left shadow-lift transition-transform duration-100 motion-safe:animate-pop-in motion-safe:active:scale-[0.97]`}
              data-testid={`quick-${item.key}`}
            >
              <span className="relative isolate flex h-11 w-11 shrink-0 items-center justify-center" aria-hidden="true">
                <span className={`relative flex h-11 w-11 items-center justify-center rounded-full shadow-lift [&>svg]:h-5 [&>svg]:w-5 ${look.icon}`}>{look.glyph}</span>
                {look.live}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold leading-tight text-text-primary">{item.label}</span>
                <span className="mt-0.5 block text-micro leading-snug text-text-secondary">{item.sub}</span>
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
