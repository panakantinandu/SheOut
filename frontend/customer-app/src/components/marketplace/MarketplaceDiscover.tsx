import { ArrowRight, Check, LayoutGrid, MessageCircle, Pencil, Pointer, ShieldCheck, Sparkles, Wallet } from 'lucide-react';
import { marketplaceArt, useTranslation } from '@sheout/design-system';
import type { SellerCategory } from '../../api/types';
import { SELLER_CATEGORIES } from '../../lib/seller';

/*
 * What the marketplace shows before she has searched or filtered for
 * anything, in the order she needs it: what this place is, then the six
 * things she can find here as big pictures, then what is for sale, then how
 * buying works, and last - for the few who came to sell - how to open a
 * shop. Pictures carry every step, so it works for someone who reads little.
 * Every piece is true on day one with no shops at all.
 */

/**
 * The marketplace's opening card. With no shops live yet, its badge and
 * line say the marketplace is opening, so that news sits at the top rather
 * than in a card between the categories and the list.
 */
export function MarketplaceHero({ opening }: { opening: boolean }) {
  const { t } = useTranslation();
  const peek = ['fashion', 'mehandi', 'gifts', 'ornaments'].map((key) => SELLER_CATEGORIES.find((c) => c.key === key)!.art);
  return (
    <section
      className="relative isolate overflow-hidden rounded-[1.75rem] p-4 text-white shadow-[0_14px_26px_-12px_rgba(157,23,77,0.6)] motion-safe:animate-rise-in"
      style={{ background: 'linear-gradient(135deg, #EC4899 0%, #BE185D 55%, #6B1D8F 110%)' }}
      data-testid="market-hero"
    >
      <span aria-hidden="true" className="pointer-events-none absolute -left-12 -top-14 -z-10 h-44 w-44 rounded-full bg-white/20 blur-2xl motion-safe:animate-drift" />
      <span aria-hidden="true" className="pointer-events-none absolute -bottom-16 -right-10 -z-10 h-48 w-48 rounded-full bg-amber-300/30 blur-2xl motion-safe:animate-drift-slow" />
      <span aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-0 -z-10 w-1/3 bg-gradient-to-r from-transparent via-white/15 to-transparent motion-safe:animate-sheen" />

      <div className="relative z-10">
        <span className="inline-flex items-center gap-1.5 rounded-full bg-white/20 px-2.5 py-0.5 text-micro font-semibold uppercase tracking-wide backdrop-blur-sm">
          {opening ? (
            <span className="relative flex h-1.5 w-1.5" aria-hidden="true">
              <span className="absolute inset-0 rounded-full bg-emerald-300 motion-safe:animate-pulse-ring" />
              <span className="relative h-1.5 w-1.5 rounded-full bg-emerald-300" />
            </span>
          ) : (
            <Sparkles className="h-3 w-3" aria-hidden="true" />
          )}
          {opening ? t('seller.discover.openingBadge') : t('seller.discover.eyebrow')}
        </span>
        {/* On one line, across the card; the picture waits in the corner below it. */}
        <h2 className="mt-2 whitespace-nowrap font-heading text-section leading-tight [text-shadow:0_1px_2px_rgba(0,0,0,0.2)]" data-testid="market-hero-title">{t('seller.discover.title')}</h2>
        {/* The words on the left, the picture beside them - in one row under
            the title, so however long the title or the words run in her
            language, the picture never sits under the title. */}
        <div className="mt-3 flex items-center gap-2">
          <div className="min-w-0 flex-1">
            <p className="text-caption opacity-90">{opening ? t('seller.discover.openingBody') : t('seller.discover.body')}</p>
            {/* What is inside, at a glance: a few of the categories as little
                photos popping in one after another, each floating on its own beat. */}
            <div className="mt-2.5 flex items-center" aria-hidden="true" data-testid="market-hero-peek">
              {peek.map((art, i) => (
                <span
                  key={art}
                  className={`${i ? '-ml-2.5' : ''} relative motion-safe:animate-pop-in`}
                  style={{ animationDelay: `${350 + i * 120}ms`, zIndex: peek.length - i }}
                >
                  <img
                    src={art}
                    alt=""
                    draggable={false}
                    className="h-9 w-9 select-none rounded-full object-cover ring-2 ring-white/90 shadow-lift motion-safe:animate-float"
                    style={{ animationDelay: `${i * -0.9}s` }}
                  />
                </span>
              ))}
              <span
                className="-ml-2.5 flex h-9 w-9 items-center justify-center rounded-full bg-white/25 text-micro font-bold ring-2 ring-white/90 backdrop-blur-sm motion-safe:animate-pop-in"
                style={{ animationDelay: `${350 + peek.length * 120}ms` }}
              >
                +{SELLER_CATEGORIES.length - peek.length}
              </span>
            </div>
            <p className="mt-1.5 text-micro font-semibold opacity-90">{t('seller.discover.heroMore')}</p>
          </div>
          <img
            src={marketplaceArt}
            alt=""
            aria-hidden="true"
            draggable={false}
            className="pointer-events-none -mb-2 -mr-2 h-24 w-24 shrink-0 select-none object-contain drop-shadow-[0_14px_16px_rgba(0,0,0,0.3)] motion-safe:animate-float"
          />
        </div>
      </div>
    </section>
  );
}

/**
 * "What are you looking for?": the categories as the screen's main
 * control - big pictures two to a row, each with its name and what is in
 * it, so she can find her way by picture alone. A light sweeps across them
 * one after another, and a tapping finger beside the heading shows what to
 * do without needing the words.
 */
export function CategoryGrid({ onPick }: { onPick: (value: SellerCategory) => void }) {
  const { t } = useTranslation();
  const named = SELLER_CATEGORIES.filter((c) => c.value !== 'OTHER');
  const other = SELLER_CATEGORIES.find((c) => c.value === 'OTHER');
  return (
    <section data-testid="market-categories">
      <div className="mb-3 flex items-end justify-between gap-3">
        <div className="min-w-0">
          <h2 className="font-heading text-section text-text-primary">{t('seller.discover.byCategory')}</h2>
          <p className="mt-0.5 flex items-center gap-1.5 text-caption font-medium text-primary">
            <Pointer className="h-4 w-4 shrink-0 motion-safe:animate-tap-hint" aria-hidden="true" />
            {t('seller.discover.byCategoryHint')}
          </p>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-3">
        {named.map((c, i) => (
          <button
            key={c.value}
            type="button"
            onClick={() => onPick(c.value)}
            style={{ animationDelay: `${140 + i * 70}ms` }}
            className="group relative isolate flex min-w-0 flex-col overflow-hidden rounded-[1.5rem] bg-surface text-left shadow-lift ring-1 ring-border transition-transform duration-150 motion-safe:animate-pop-in motion-safe:active:scale-95"
            data-testid={`market-category-${c.key}`}
          >
            <CategoryPhoto art={c.art} index={i} className="aspect-[4/3] w-full" />
            <span className={`block px-3 pb-3 pt-2 ${c.tint}`}>
              <span className="block font-heading text-card-title leading-tight text-text-primary">{t(`seller.categories.${c.key}`)}</span>
              <span className="mt-0.5 block truncate text-caption leading-snug text-text-secondary">{t(`seller.discover.hints.${c.key}`)}</span>
            </span>
          </button>
        ))}
        {/* Anything else: the whole row, the photo on one side and, on the other, an invitation to say what. */}
        {other && (
          <button
            type="button"
            onClick={() => onPick(other.value)}
            style={{ animationDelay: `${140 + named.length * 70}ms` }}
            className="group relative isolate col-span-2 flex min-w-0 overflow-hidden rounded-[1.5rem] bg-surface text-left shadow-lift ring-1 ring-border transition-transform duration-150 motion-safe:animate-pop-in motion-safe:active:scale-[0.98]"
            data-testid={`market-category-${other.key}`}
          >
            <CategoryPhoto art={other.art} index={named.length} className="w-[44%] shrink-0" />
            <span className={`flex min-w-0 flex-1 flex-col justify-center gap-1 px-3 py-3 ${other.tint}`}>
              <span className="font-heading text-card-title leading-tight text-text-primary">{t(`seller.categories.${other.key}`)}</span>
              <span className="text-caption leading-snug text-text-secondary">{t(`seller.discover.hints.${other.key}`)}</span>
              <span className="mt-1 inline-flex items-center gap-1.5 self-start rounded-full bg-surface px-2.5 py-1 text-micro font-semibold text-primary shadow-lift">
                <Pencil className="h-3 w-3 motion-safe:animate-tap-hint" aria-hidden="true" />
                {t('seller.discover.otherPrompt.type')}
              </span>
            </span>
          </button>
        )}
      </div>
    </section>
  );
}

/**
 * A category's photo, alive: drifting slowly closer and back (each on its
 * own beat, so the grid never moves as one), with a light passing across the
 * tiles in reading order and a soft shade at its foot.
 */
function CategoryPhoto({ art, index, className }: { art: string; index: number; className: string }) {
  return (
    <span className={`relative block overflow-hidden ${className}`}>
      <img
        src={art}
        alt=""
        aria-hidden="true"
        loading="lazy"
        draggable={false}
        className="h-full w-full select-none object-cover motion-safe:animate-ken-burns"
        style={{ animationDelay: `${index * -2.3}s` }}
      />
      <span aria-hidden="true" className="pointer-events-none absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t from-black/20 to-transparent" />
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-y-0 left-0 w-1/2 bg-gradient-to-r from-transparent via-white/35 to-transparent motion-safe:animate-sheen"
        style={{ animationDelay: `${1 + index * 0.45}s` }}
      />
    </span>
  );
}

/**
 * Once she has picked a category, the six stay in reach as a row of
 * pictures, the ones she is looking at lit, so switching is one tap on a
 * picture rather than a trip into the filter sheet. "All" goes back to
 * browsing everything.
 */
export function CategoryRail({ selected, onPick, onAll }: {
  selected: SellerCategory[];
  onPick: (value: SellerCategory) => void;
  onAll: () => void;
}) {
  const { t } = useTranslation();
  const pill = 'flex shrink-0 snap-start flex-col items-center gap-1 rounded-2xl border-2 px-2 pb-1.5 pt-1.5 transition-colors duration-200 motion-safe:active:scale-95';
  return (
    <nav
      aria-label={t('seller.browse.categories')}
      className="-mx-screen flex snap-x scroll-px-screen gap-2 overflow-x-auto px-screen pb-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      data-testid="market-category-rail"
    >
      <button
        type="button"
        onClick={onAll}
        aria-pressed={selected.length === 0}
        className={`${pill} w-[4.75rem] ${selected.length === 0 ? 'border-primary bg-primary-light' : 'border-transparent bg-surface'}`}
      >
        <span className="flex h-12 w-12 items-center justify-center rounded-full bg-primary text-text-inverse">
          <LayoutGrid className="h-5 w-5" aria-hidden="true" />
        </span>
        <span className="text-micro font-semibold leading-tight text-text-primary">{t('seller.discover.all')}</span>
      </button>
      {SELLER_CATEGORIES.map((c, i) => {
        const on = selected.includes(c.value);
        return (
          <button
            key={c.value}
            type="button"
            onClick={() => onPick(c.value)}
            aria-pressed={on}
            style={{ animationDelay: `${i * 40}ms` }}
            className={`${pill} w-[4.75rem] motion-safe:animate-fade-slide-in ${on ? 'border-primary bg-primary-light shadow-lift' : 'border-transparent bg-surface'}`}
            data-testid={`rail-category-${c.key}`}
          >
            <span className={`relative flex h-12 w-12 items-center justify-center rounded-full ${c.tint}`}>
              <img src={c.art} alt="" aria-hidden="true" loading="lazy" draggable={false} className={`h-12 w-12 rounded-full object-cover transition-transform duration-300 ${on ? 'scale-110' : ''}`} />
              {on && (
                <span className="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-primary text-text-inverse ring-2 ring-surface motion-safe:animate-pop-in">
                  <Check className="h-2.5 w-2.5" strokeWidth={3.5} aria-hidden="true" />
                </span>
              )}
            </span>
            <span className={`line-clamp-2 text-center text-micro font-semibold leading-tight ${on ? 'text-primary' : 'text-text-primary'}`}>
              {t(`seller.categories.${c.key}`)}
            </span>
          </button>
        );
      })}
    </nav>
  );
}

/**
 * How buying works, for the buyer: pick, contact her, pay her. Each step
 * also carries one thing she can count on - the seller is verified, there
 * is no middleman, SheOut takes nothing - so this is the only "why trust
 * this" on the screen. The line joining the steps draws itself down.
 */
export function HowItWorks() {
  const { t } = useTranslation();
  const steps = [
    { icon: <ShieldCheck />, tone: 'bg-accent-green', title: t('seller.discover.how.step1Title'), body: t('seller.discover.how.step1Body') },
    { icon: <MessageCircle />, tone: 'bg-accent-blue', title: t('seller.discover.how.step2Title'), body: t('seller.discover.how.step2Body') },
    { icon: <Wallet />, tone: 'bg-accent-orange', title: t('seller.discover.how.step3Title'), body: t('seller.discover.how.step3Body') },
  ];
  return (
    <section className="rounded-[1.5rem] border border-border bg-surface p-4 shadow-lift" data-testid="market-how">
      <h2 className="font-heading text-card-title text-text-primary">{t('seller.discover.how.title')}</h2>
      <ol className="relative mt-4 space-y-5">
        <span aria-hidden="true" className="absolute bottom-5 left-[1.375rem] top-5 w-0.5 origin-top rounded-full bg-gradient-to-b from-accent-green via-accent-blue to-accent-orange opacity-50 motion-safe:animate-fill-y" />
        {steps.map((step, i) => (
          <li key={step.title} className="relative flex items-start gap-3 motion-safe:animate-fade-slide-in" style={{ animationDelay: `${250 + i * 180}ms` }}>
            <span className={`relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${step.tone} text-white shadow-lift [&>svg]:h-5 [&>svg]:w-5`} aria-hidden="true">
              {step.icon}
              <span className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-surface text-[0.6875rem] font-bold text-text-primary shadow-lift">
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
 * The seller's way in, last on the screen and labelled "For sellers", so a
 * buyer never mistakes it for something she has to do. Opens the same place
 * as the drawer's "Sell on SheOut": registration for a new seller, her shop
 * for an existing one. Every claim on it is one the marketplace keeps:
 * sellers pay a listing fee, SheOut takes nothing from a sale, and buyers
 * contact her directly.
 */
export function SellCard({ onOpen }: { onOpen: () => void }) {
  const { t } = useTranslation();
  const points = [t('seller.discover.sell.point1'), t('seller.discover.sell.point2')];
  const arts = ['fashion', 'mehandi', 'gifts'].map((key) => SELLER_CATEGORIES.find((c) => c.key === key)!.art);

  return (
    // A frame of light travelling round the card: purple, pink and orange
    // chasing each other along its edge, slowly - the one moving border on
    // the screen, so the way in for sellers is found without being loud.
    <section className="relative isolate overflow-hidden rounded-[1.75rem] p-[2px] shadow-float" data-testid="market-sell">
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-[-60%] -z-10 motion-safe:animate-[spin_7s_linear_infinite]"
        style={{ background: 'conic-gradient(from 0deg, #7B3FE4, #EC4899, #F59E0B, #7B3FE4)' }}
      />
      <div className="relative isolate overflow-hidden rounded-[calc(1.75rem-2px)] bg-surface p-5">
        <span aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10 bg-gradient-to-br from-primary-light via-surface to-accent-orange-tint" />
        <span aria-hidden="true" className="pointer-events-none absolute -right-16 -top-16 -z-10 h-48 w-48 rounded-full bg-accent-orange/25 blur-2xl motion-safe:animate-drift" />
        <span aria-hidden="true" className="pointer-events-none absolute -bottom-20 -left-10 -z-10 h-44 w-44 rounded-full bg-primary/15 blur-2xl motion-safe:animate-drift-slow" />
        {/* Sparkles twinkling at the corners, each on its own beat. */}
        {[
          { cls: 'right-4 top-4 h-4 w-4 text-accent-orange', d: '0s' },
          { cls: 'right-[42%] top-3 h-3 w-3 text-primary', d: '0.8s' },
          { cls: 'bottom-[4.5rem] right-3 h-3.5 w-3.5 text-pink-500', d: '1.5s' },
        ].map((spark) => (
          <Sparkles key={spark.d} aria-hidden="true" className={`pointer-events-none absolute ${spark.cls} motion-safe:animate-twinkle`} style={{ animationDelay: spark.d }} />
        ))}

        <span className="inline-flex items-center gap-1.5 rounded-full bg-gradient-to-r from-pink-600 via-fuchsia-600 to-primary px-3 py-1 text-micro font-bold uppercase tracking-wide text-white shadow-lift" data-testid="market-sell-badge">
          <span className="relative flex h-2 w-2">
            <span className="absolute inset-0 rounded-full bg-white motion-safe:animate-pulse-ring" />
            <span className="relative h-2 w-2 rounded-full bg-white" />
          </span>
          {t('seller.discover.sell.eyebrow')}
        </span>
        {/* On one line, across the card. */}
        <p className="mt-3 whitespace-nowrap font-heading text-section leading-tight text-text-primary" data-testid="market-sell-title">
          {t('seller.discover.sell.title')}
        </p>

        {/* What it is on the left; the pictures lower, beside it, not beside the title. */}
        <div className="mt-2 flex gap-3">
          <div className="min-w-0 flex-1">
            <p className="text-caption text-text-secondary">{t('seller.discover.sell.body')}</p>
            <ul className="mt-2.5 space-y-1">
              {points.map((point, i) => (
                <li
                  key={point}
                  className="flex items-center gap-1.5 text-caption font-medium text-text-primary motion-safe:animate-fade-slide-in"
                  style={{ animationDelay: `${300 + i * 150}ms` }}
                >
                  <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-accent-green text-white">
                    <Check className="h-2.5 w-2.5" strokeWidth={3.5} aria-hidden="true" />
                  </span>
                  {point}
                </li>
              ))}
            </ul>
          </div>

          {/* Three of the category pictures, fanned like cards in a hand, each hovering on its own beat. */}
          <div aria-hidden="true" className="relative mt-1 h-32 w-[6.5rem] shrink-0">
            {arts.map((art, i) => (
              <span key={art} className={`absolute ${['left-0 top-7 z-0 -rotate-12', 'left-7 top-0 z-10 rotate-3', 'left-3 top-[4.5rem] z-20 rotate-6'][i]}`}>
                <img
                  src={art}
                  alt=""
                  loading="lazy"
                  draggable={false}
                  className="h-[3.75rem] w-[3.75rem] rounded-2xl bg-surface object-cover ring-2 ring-surface shadow-float motion-safe:animate-bob"
                  style={{ animationDelay: `${i * -1.6}s` }}
                />
              </span>
            ))}
          </div>
        </div>

        <button
          type="button"
          onClick={onOpen}
          // The brand gradient, and a breath of purple going out from it: an invitation.
          className="group relative mt-4 flex w-full items-center justify-center gap-2 overflow-hidden rounded-full bg-gradient-to-r from-primary via-primary-mid to-pink-600 px-4 py-3 font-heading text-sm text-white shadow-float transition-transform duration-100 motion-safe:animate-glow-brand motion-safe:active:scale-[0.98]"
          data-testid="market-sell-cta"
        >
          <span aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-0 w-1/3 bg-gradient-to-r from-transparent via-white/30 to-transparent motion-safe:animate-sheen" />
          {t('seller.discover.sell.cta')}
          <ArrowRight className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5" aria-hidden="true" />
        </button>
      </div>
    </section>
  );
}

/**
 * Other, opened by a buyer: there is no fixed list of what is in it, so it
 * asks her to say. "Type what you need" puts her straight in the search box
 * (sellers here describe their shops in their own words, and search reads
 * them); "Ask in your own words" is the assistant's search.
 */
export function OtherPrompt({ onType, onAsk }: { onType: () => void; onAsk: () => void }) {
  const { t } = useTranslation();
  return (
    <section
      className="relative isolate overflow-hidden rounded-[1.5rem] border border-primary/15 bg-gradient-to-br from-primary-light to-accent-green-tint p-4 motion-safe:animate-fade-slide-in"
      data-testid="market-other-prompt"
    >
      <Sparkles aria-hidden="true" className="pointer-events-none absolute right-4 top-4 h-4 w-4 text-primary motion-safe:animate-twinkle" />
      <p className="pr-6 font-heading text-card-title text-text-primary">{t('seller.discover.otherPrompt.title')}</p>
      <p className="mt-1 text-caption text-text-secondary">{t('seller.discover.otherPrompt.body')}</p>
      <div className="mt-3 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={onType}
          className="inline-flex items-center gap-1.5 rounded-full bg-primary px-3.5 py-2 text-caption font-semibold text-text-inverse shadow-lift transition-transform duration-100 motion-safe:active:scale-95"
          data-testid="other-type"
        >
          <Pencil className="h-3.5 w-3.5" aria-hidden="true" />
          {t('seller.discover.otherPrompt.type')}
        </button>
        <button
          type="button"
          onClick={onAsk}
          className="inline-flex items-center gap-1.5 rounded-full bg-surface px-3.5 py-2 text-caption font-semibold text-primary shadow-lift transition-transform duration-100 motion-safe:active:scale-95"
          data-testid="other-ask"
        >
          <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
          {t('seller.discover.otherPrompt.ask')}
        </button>
      </div>
    </section>
  );
}
