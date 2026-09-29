import { ChevronRight } from 'lucide-react';
import { useCountUp, useTranslation } from '@sheout/design-system';

const rupees = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });

/**
 * Today, at a glance: the money large enough to read off a handlebar mount
 * in sunlight, the two counts smaller beneath it. The whole card is the way
 * to the Earnings tab.
 * <p>
 * Counts up once when the figure first arrives (the design system's
 * useCountUp, which shows the final number at once under reduced motion).
 */
export function EarningsCard({ amount, rides, activeTrips, onOpen }: { amount: number; rides: number; activeTrips: number; onOpen: () => void }) {
  const { t } = useTranslation();
  const shownAmount = useCountUp(amount, true);
  const shownRides = Math.round(useCountUp(rides, true));
  const shownActive = Math.round(useCountUp(activeTrips, true));

  return (
    <button
      type="button"
      onClick={onOpen}
      className="block w-full rounded-card bg-surface p-4 text-left shadow-card transition-transform duration-100 motion-safe:active:scale-[0.99]"
      data-testid="earnings-card"
      aria-label={t('home.openEarnings')}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium text-text-secondary">{t('home.earnedToday')}</p>
          <p className="mt-1 font-heading text-[2.5rem] font-bold leading-none tabular-nums text-text-primary" data-testid="earnings-today">
            ₹{rupees.format(Math.round(shownAmount))}
          </p>
        </div>
        <span className="flex shrink-0 items-center gap-2">
          {/* A rupee coin turning over once as the figure arrives. */}
          <span className="flex h-10 w-10 items-center justify-center rounded-full bg-amber-300 bg-gradient-to-br from-amber-200 to-amber-400 font-heading text-lg font-bold text-amber-900 shadow-lift ring-2 ring-amber-200 [perspective:400px] motion-safe:animate-coin-flip" aria-hidden="true" data-testid="earnings-coin">
            ₹
          </span>
          <ChevronRight className="h-6 w-6 text-text-secondary" aria-hidden="true" />
        </span>
      </div>
      <div className="mt-4 grid grid-cols-2 gap-3 border-t border-border pt-3">
        <Metric value={shownRides} label={t('home.ridesToday')} />
        <Metric value={shownActive} label={t('home.activeTrips')} />
      </div>
    </button>
  );
}

function Metric({ value, label }: { value: number; label: string }) {
  return (
    <div className="min-w-0">
      <p className="font-heading text-2xl font-bold tabular-nums text-text-primary">{value}</p>
      <p className="break-words text-xs text-text-secondary">{label}</p>
    </div>
  );
}
