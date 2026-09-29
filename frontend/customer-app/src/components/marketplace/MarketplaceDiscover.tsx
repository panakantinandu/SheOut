import { ArrowRight, MessageCircle, Search, ShieldCheck, Sparkles, Store, Wallet } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from '@sheout/design-system';
import type { SellerCategory } from '../../api/types';
import { SELLER_CATEGORIES } from '../../lib/seller';

/*
 * What the marketplace shows before she has searched or filtered for
 * anything: a way to browse, not a blank list. Every piece here is true on
 * day one with no shops at all - the six categories, how buying works, and
 * what SheOut does and does not do in a sale - so the screen is worth
 * opening before the first seller is live, and still earns its place after.
 */

/** The marketplace's opening card: what it is, over three of its category pictures. */
export function MarketplaceHero() {
  const { t } = useTranslation();
  const arts = ['fashion', 'ornaments', 'mehandi'].map((key) => SELLER_CATEGORIES.find((c) => c.key === key)!.art);
  return (
    <section
      className="relative isolate overflow-hidden rounded-[1.75rem] p-5 text-white shadow-float motion-safe:animate-fade-slide-in"
      style={{ background: 'linear-gradient(135deg, #7B3FE4 0%, #4A1A9E 50%, #C2410C 130%)' }}
      data-testid="market-hero"
    >
      <span aria-hidden="true" className="pointer-events-none absolute -left-12 -top-14 -z-10 h-44 w-44 rounded-full bg-white/15 blur-2xl motion-safe:animate-drift" />
      <span aria-hidden="true" className="pointer-events-none absolute -bottom-16 -right-10 -z-10 h-48 w-48 rounded-full bg-accent-orange/40 blur-2xl motion-safe:animate-drift-slow" />
      <span aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-0 -z-10 w-1/3 bg-gradient-to-r from-transparent via-white/15 to-transparent motion-safe:animate-sheen" />

      <div className="relative z-10 max-w-[64%]">
        <span className="inline-flex items-center gap-1 rounded-full bg-white/20 px-2.5 py-0.5 text-micro font-semibold uppercase tracking-wide backdrop-blur-sm">
          <Sparkles className="h-3 w-3" aria-hidden="true" />
          {t('seller.discover.eyebrow')}
        </span>
        <h2 className="mt-2 font-heading text-section leading-tight [text-shadow:0_1px_2px_rgba(0,0,0,0.2)]">{t('seller.discover.title')}</h2>
        <p className="mt-1 text-caption opacity-90">{t('seller.discover.body')}</p>
      </div>

      {/* Three pictures, stacked and hovering on their own beats. */}
      <div aria-hidden="true" className="absolute -right-1 bottom-3 top-4 w-[38%]">
        {arts.map((art, i) => (
          <img
            key={art}
            src={art}
            alt=""
            draggable={false}
            className={`absolute select-none object-contain drop-shadow-[0_12px_14px_rgba(0,0,0,0.3)] motion-safe:animate-bob ${
              ['right-1 top-0 h-[4.5rem] w-[4.5rem]', 'left-0 top-1/2 h-16 w-16 -translate-y-1/2', 'bottom-0 right-4 h-[4.5rem] w-[4.5rem]'][i]
            }`}
            style={{ animationDelay: `${i * -1.7}s` }}
          />
        ))}
      </div>
    </section>
  );
}

/** Browse by category: the six, as big picture tiles, each narrowing the list to itself. */
export function CategoryGrid({ onPick }: { onPick: (value: SellerCategory) => void }) {
  const { t } = useTranslation();
  return (
    <section data-testid="market-categories">
      <h2 className="mb-3 font-heading text-section text-text-primary">{t('seller.discover.byCategory')}</h2>
      <div className="grid grid-cols-3 gap-3">
        {SELLER_CATEGORIES.map((c, i) => (
          <button
            key={c.value}
            type="button"
            onClick={() => onPick(c.value)}
            style={{ animationDelay: `${120 + i * 60}ms` }}
            className={`group relative flex flex-col items-center overflow-hidden rounded-card ${c.tint} px-1.5 pb-3 pt-2 text-center shadow-lift transition-transform duration-150 motion-safe:animate-pop-in motion-safe:active:scale-95`}
            data-testid={`market-category-${c.key}`}
          >
            {/* A soft disc of light behind the picture, so each one sits on a stage. */}
            <span aria-hidden="true" className="absolute left-1/2 top-3 h-16 w-16 -translate-x-1/2 rounded-full bg-surface/70 blur-md" />
            <img
              src={c.art}
              alt=""
              aria-hidden="true"
              loading="lazy"
              draggable={false}
              className="relative h-[4.5rem] w-[4.5rem] object-contain drop-shadow-[0_8px_10px_rgba(74,26,158,0.18)] transition-transform duration-300 ease-out group-hover:-translate-y-1 group-hover:scale-105"
            />
            <span className="relative mt-1 text-caption font-semibold leading-tight text-text-primary">{t(`seller.categories.${c.key}`)}</span>
          </button>
        ))}
      </div>
    </section>
  );
}

/** What she can count on here, in three short lines. */
export function TrustStrip() {
  const { t } = useTranslation();
  const items: { icon: ReactNode; title: string; body: string; tone: string }[] = [
    { icon: <ShieldCheck />, title: t('seller.discover.trust.verifiedTitle'), body: t('seller.discover.trust.verifiedBody'), tone: 'bg-accent-green-tint text-accent-green-strong' },
    { icon: <MessageCircle />, title: t('seller.discover.trust.directTitle'), body: t('seller.discover.trust.directBody'), tone: 'bg-accent-blue-tint text-accent-blue-strong' },
    { icon: <Wallet />, title: t('seller.discover.trust.feeTitle'), body: t('seller.discover.trust.feeBody'), tone: 'bg-accent-orange-tint text-accent-orange-strong' },
  ];
  return (
    <section
      className="-mx-screen flex snap-x scroll-px-screen gap-3 overflow-x-auto px-screen pb-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      aria-label={t('seller.discover.trust.label')}
      data-testid="market-trust"
    >
      {items.map((item, i) => (
        <div
          key={item.title}
          style={{ animationDelay: `${260 + i * 80}ms` }}
          className="flex w-[72%] max-w-[15rem] shrink-0 snap-start items-start gap-3 rounded-card border border-border bg-surface p-3 shadow-lift motion-safe:animate-fade-slide-in"
        >
          <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full [&>svg]:h-5 [&>svg]:w-5 ${item.tone}`} aria-hidden="true">
            {item.icon}
          </span>
          <div className="min-w-0">
            <p className="text-sm font-semibold text-text-primary">{item.title}</p>
            <p className="mt-0.5 text-caption text-text-secondary">{item.body}</p>
          </div>
        </div>
      ))}
    </section>
  );
}

/** How buying works here: find, contact, agree - joined by a line so it reads as one path. */
export function HowItWorks() {
  const { t } = useTranslation();
  const steps = [
    { icon: <Search />, title: t('seller.discover.how.step1Title'), body: t('seller.discover.how.step1Body') },
    { icon: <MessageCircle />, title: t('seller.discover.how.step2Title'), body: t('seller.discover.how.step2Body') },
    { icon: <Store />, title: t('seller.discover.how.step3Title'), body: t('seller.discover.how.step3Body') },
  ];
  return (
    <section className="rounded-card border border-border bg-surface p-4 shadow-lift" data-testid="market-how">
      <h2 className="font-heading text-card-title text-text-primary">{t('seller.discover.how.title')}</h2>
      <ol className="relative mt-3 space-y-4">
        <span aria-hidden="true" className="absolute bottom-4 left-[1.125rem] top-4 w-0.5 rounded-full bg-gradient-to-b from-primary via-primary-mid to-accent-orange opacity-40" />
        {steps.map((step, i) => (
          <li key={step.title} className="relative flex items-start gap-3">
            <span className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary text-text-inverse shadow-lift [&>svg]:h-4 [&>svg]:w-4" aria-hidden="true">
              {step.icon}
              <span className="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-accent-orange text-[0.625rem] font-bold text-white ring-2 ring-surface">
                {i + 1}
              </span>
            </span>
            <div className="min-w-0 pt-0.5">
              <p className="text-sm font-semibold text-text-primary">{step.title}</p>
              <p className="text-caption text-text-secondary">{step.body}</p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}

/**
 * In place of "No shops yet": the marketplace is opening, not empty. Says
 * so plainly, and offers the one thing she can do about it today - open a
 * shop of her own.
 */
export function LaunchCard({ onSell }: { onSell: () => void }) {
  const { t } = useTranslation();
  return (
    <section
      className="relative isolate overflow-hidden rounded-card border border-primary/15 bg-primary-light p-5 text-center motion-safe:animate-fade-slide-in"
      data-testid="market-launch"
    >
      <span aria-hidden="true" className="pointer-events-none absolute -right-12 -top-12 -z-10 h-40 w-40 rounded-full bg-accent-orange/25 blur-2xl motion-safe:animate-drift" />
      <span className="relative mx-auto flex h-14 w-14 items-center justify-center">
        <span aria-hidden="true" className="absolute inset-2 rounded-full bg-primary/40 motion-safe:animate-pulse-ring" />
        <span className="relative flex h-14 w-14 items-center justify-center rounded-full bg-surface text-primary shadow-float">
          <Store className="h-6 w-6" aria-hidden="true" />
        </span>
      </span>
      <span className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-surface px-2.5 py-0.5 text-micro font-semibold uppercase tracking-wide text-primary">
        <span className="h-1.5 w-1.5 rounded-full bg-accent-green" aria-hidden="true" />
        {t('seller.discover.launch.badge')}
      </span>
      <h2 className="mt-2 font-heading text-section text-text-primary">{t('seller.discover.launch.title')}</h2>
      <p className="mx-auto mt-1 max-w-[18rem] text-sm text-text-secondary">{t('seller.discover.launch.body')}</p>
      <button
        type="button"
        onClick={onSell}
        className="group relative mx-auto mt-4 flex items-center justify-center gap-2 overflow-hidden rounded-full bg-primary px-5 py-3 font-heading text-sm text-text-inverse shadow-float transition-transform duration-100 motion-safe:active:scale-[0.98]"
        data-testid="market-launch-sell"
      >
        <span aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-0 w-1/3 bg-gradient-to-r from-transparent via-white/25 to-transparent motion-safe:animate-sheen" />
        {t('seller.discover.launch.cta')}
        <ArrowRight className="h-4 w-4" aria-hidden="true" />
      </button>
    </section>
  );
}
