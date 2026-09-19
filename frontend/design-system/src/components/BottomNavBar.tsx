import { useEffect, useRef, useState, type ReactNode } from 'react';
import { cn } from '../lib/cn';

export interface BottomNavItem {
  key: string;
  label: string;
  icon: ReactNode;
  active?: boolean;
  /** The floating circular center item (SOS on customer screens). */
  raised?: boolean;
  onClick?: () => void;
}

export interface BottomNavBarProps {
  items: BottomNavItem[];
  className?: string;
}

/**
 * Home / Bookings / Wallet / Profile row, with support for one raised
 * circular item (SOS) that floats above the bar.
 * <p>
 * WHICH TAB YOU ARE ON IS NOT SAID IN COLOUR ALONE. It used to be: the
 * selected tab turned purple and nothing else changed - same icon, same
 * weight, same label - which is a weak signal at arm's length on a phone in
 * daylight, and no signal at all to somebody who cannot separate purple from
 * grey. Colour is now the last of four cues, not the only one: the selected
 * icon sits in a filled pill, is drawn larger and heavier, and its label
 * turns semibold. Any one of them answers "where am I" on its own.
 * <p>
 * AND A TAP ANSWERS IMMEDIATELY. Nothing happened on press before, so on a
 * slow screen a tap looked ignored and got repeated. Now the tab gives under
 * the finger the moment it is touched - CSS on :active, so the handler has
 * already fired - and the icon of the tab you land on springs up once as it
 * takes the pill. Both are motion-safe: somebody who asked their device for
 * less movement gets the same states with none of the movement.
 */
export function BottomNavBar({ items, className }: BottomNavBarProps) {
  const activeKey = items.find((item) => item.active)?.key ?? null;
  const previousActive = useRef(activeKey);
  // The tab that has just been switched TO, so the spring plays on arrival
  // rather than on every render - and not at all on first paint, where a
  // screen loading is not a tab being chosen.
  const [justArrived, setJustArrived] = useState<string | null>(null);

  useEffect(() => {
    if (previousActive.current === activeKey) return;
    previousActive.current = activeKey;
    setJustArrived(activeKey);
  }, [activeKey]);

  return (
    <nav
      className={cn(
        // The bar sits over scrolling content, so it needs an edge of its own.
        'flex items-end justify-between rounded-t-card border-t border-border bg-surface px-2 pt-2 shadow-card',
        // Clear of the iPhone home indicator, without a gap on everything else.
        'pb-[max(0.5rem,env(safe-area-inset-bottom))]',
        className
      )}
    >
      {items.map((item) =>
        item.raised ? (
          <button
            key={item.key}
            type="button"
            onClick={item.onClick}
            aria-current={item.active ? 'page' : undefined}
            className="group flex flex-1 flex-col items-center gap-1"
          >
            {/* Ringed in the bar's own colour so the circle reads as sitting
                above the bar rather than punched into it - and stays legible
                when the map or a card scrolls behind it. */}
            <span
              className={cn(
                '-mt-9 flex h-16 w-16 items-center justify-center rounded-full bg-danger text-text-inverse',
                'shadow-raised ring-4 ring-surface [&_svg]:h-7 [&_svg]:w-7',
                'transition-transform duration-100 motion-safe:group-active:scale-90'
              )}
            >
              {item.icon}
            </span>
            {/* Semibold, unlike the tab labels: this is the one thing here
                that is not a place to browse. */}
            <span className="text-xs font-semibold text-danger">{item.label}</span>
          </button>
        ) : (
          <button
            key={item.key}
            type="button"
            onClick={item.onClick}
            aria-current={item.active ? 'page' : undefined}
            className="group flex flex-1 flex-col items-center gap-1 py-1"
          >
            <span
              className={cn(
                'relative flex h-8 w-14 items-center justify-center',
                'transition-transform duration-100 motion-safe:group-active:scale-90'
              )}
            >
              {item.active && (
                <span className="absolute inset-0 rounded-full bg-primary-light motion-safe:animate-pop-in" aria-hidden="true" />
              )}
              <span
                key={item.active ? 'on' : 'off'}
                className={cn(
                  'relative',
                  item.active
                    ? 'text-primary [&_svg]:h-[22px] [&_svg]:w-[22px] [&_svg]:[stroke-width:2.4]'
                    : 'text-text-secondary [&_svg]:h-5 [&_svg]:w-5 [&_svg]:[stroke-width:1.75]',
                  item.active && justArrived === item.key && 'motion-safe:animate-nav-pop'
                )}
                onAnimationEnd={() => setJustArrived((current) => (current === item.key ? null : current))}
              >
                {item.icon}
              </span>
            </span>
            <span className={cn('text-xs', item.active ? 'font-semibold text-primary' : 'font-medium text-text-secondary')}>
              {item.label}
            </span>
          </button>
        )
      )}
    </nav>
  );
}
