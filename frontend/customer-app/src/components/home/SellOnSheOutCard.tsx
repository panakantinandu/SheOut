import { ArrowRight, Check } from 'lucide-react';
import { useTranslation } from '@sheout/design-system';

/**
 * "Sell on SheOut" on Home, so a woman who has never opened the drawer
 * still learns she can open a shop here. Opens the same place as the
 * drawer's entry: registration for a new seller, her shop for an existing one.
 * <p>
 * Every claim on it is one the marketplace keeps: sellers pay a listing fee
 * and SheOut takes nothing from a sale, and buyers contact her directly.
 */
export function SellOnSheOutCard({ arts, onOpen }: { arts: string[]; onOpen: () => void }) {
  const { t } = useTranslation();
  const points = [t('home.sell.point1'), t('home.sell.point2')];

  return (
    <div
      className="relative isolate overflow-hidden rounded-[1.75rem] border border-primary/15 bg-primary-light p-5 shadow-lift"
      data-testid="home-sell-card"
    >
      <span aria-hidden="true" className="pointer-events-none absolute -right-16 -top-16 -z-10 h-48 w-48 rounded-full bg-accent-orange/25 blur-2xl motion-safe:animate-drift" />
      <span aria-hidden="true" className="pointer-events-none absolute -bottom-20 -left-10 -z-10 h-44 w-44 rounded-full bg-primary/15 blur-2xl motion-safe:animate-drift-slow" />

      <div className="flex gap-3">
        <div className="min-w-0 flex-1">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-surface px-2.5 py-1 text-micro font-semibold uppercase tracking-wide text-primary shadow-lift">
            <span className="relative flex h-2 w-2">
              <span className="absolute inset-0 rounded-full bg-accent-orange motion-safe:animate-pulse-ring" />
              <span className="relative h-2 w-2 rounded-full bg-accent-orange" />
            </span>
            {t('home.sell.eyebrow')}
          </span>
          <p className="mt-2.5 font-heading text-section leading-tight text-text-primary">{t('home.sell.title')}</p>
          <p className="mt-1 text-caption text-text-secondary">{t('home.sell.body')}</p>
          <ul className="mt-2.5 space-y-1">
            {points.map((point) => (
              <li key={point} className="flex items-center gap-1.5 text-caption font-medium text-text-primary">
                <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-accent-green text-white">
                  <Check className="h-2.5 w-2.5" strokeWidth={3.5} aria-hidden="true" />
                </span>
                {point}
              </li>
            ))}
          </ul>
        </div>

        {/* Three of the category pictures, fanned like cards in a hand, each hovering on its own beat. */}
        <div aria-hidden="true" className="relative h-32 w-[6.5rem] shrink-0">
          {arts.slice(0, 3).map((art, i) => (
            <span
              key={art}
              className={`absolute ${['left-0 top-7 z-0 -rotate-12', 'left-7 top-0 z-10 rotate-3', 'left-3 top-[4.5rem] z-20 rotate-6'][i]}`}
            >
              <img
                src={art}
                alt=""
                loading="lazy"
                draggable={false}
                className="h-[3.75rem] w-[3.75rem] rounded-2xl bg-surface object-contain p-1 shadow-float motion-safe:animate-bob"
                style={{ animationDelay: `${i * -1.6}s` }}
              />
            </span>
          ))}
        </div>
      </div>

      <button
        type="button"
        onClick={onOpen}
        className="group relative mt-4 flex w-full items-center justify-center gap-2 overflow-hidden rounded-full bg-primary px-4 py-3 font-heading text-sm text-text-inverse shadow-float transition-transform duration-100 motion-safe:active:scale-[0.98]"
        data-testid="home-sell-cta"
      >
        <span aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-0 w-1/3 bg-gradient-to-r from-transparent via-white/25 to-transparent motion-safe:animate-sheen" />
        {t('home.sell.cta')}
        <ArrowRight className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5" aria-hidden="true" />
      </button>
    </div>
  );
}
