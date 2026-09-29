import { Check, ImageOff, MapPin, Sparkles, Store, X } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  ListEmptyState,
  ListFilterBar,
  LoadMore,
  SkeletonList,
  TextField,
  TopHeader,
  usePagedList,
  useTranslation,
} from '@sheout/design-system';
import { marketplaceApi } from '../api/client';
import type { ListingCard, SellerCategory, SmartSearchResult } from '../api/types';
import { PriceTag } from '../components/PriceTag';
import { SELLER_CATEGORIES, asProductCode, priceText } from '../lib/seller';
import { CategoryGrid, CategoryRail, HowItWorks, MarketplaceHero, SellCard } from '../components/marketplace/MarketplaceDiscover';
import { Reveal } from '../components/Reveal';
import { useGoBack } from '../lib/useGoBack';

const CATEGORY_VALUES = new Set<string>(SELLER_CATEGORIES.map((c) => c.value));

/**
 * The directory's filters, read from and written to the address bar.
 * <p>
 * In the URL rather than in state so that opening a product and coming back,
 * or refreshing, lands on the same narrowed list instead of the whole
 * directory again. That was the old screen's quiet failure: every trip into a
 * product threw the search away. Anything unreadable in the URL (a category
 * that does not exist, a price that is not a number) is ignored, not an error.
 */
function useDirectoryFilters() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const categories = (params.get('cat') ?? '')
    .split(',')
    .filter((c): c is SellerCategory => CATEGORY_VALUES.has(c));
  const min = (params.get('min') ?? '').replace(/[^\d]/g, '');
  const max = (params.get('max') ?? '').replace(/[^\d]/g, '');
  const area = params.get('area') ?? '';
  /** 'ai' for search in her own words; absent for the ordinary search. */
  const mode = params.get('mode') === 'ai' ? 'ai' : 'exact';
  /** The words last sent to the AI search - only on Search, never per keystroke, since each costs a call. */
  const ask = params.get('ask') ?? '';

  /**
   * Typing, a price or an area replaces the current entry, so Back is not
   * a walk through every letter. A step she would call "going somewhere" -
   * into a category, to all of them, another search mode, an AI search -
   * is `step`: a history entry of its own, so Back returns from it to the
   * marketplace as it was, not straight to Home.
   */
  const set = useCallback(
    (patch: Partial<Record<'q' | 'cat' | 'min' | 'max' | 'area' | 'mode' | 'ask', string>>, step = false) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          for (const [key, value] of Object.entries(patch)) {
            if (value) next.set(key, value);
            else next.delete(key);
          }
          return next;
        },
        { replace: !step }
      );
      // A step is somewhere new - a category, another search - so it opens at the top, where its results start.
      if (step) window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
    },
    [setParams]
  );

  return { q, categories, min, max, area, mode, ask, set };
}

/**
 * Marketplace: a directory of women selling from home, reached from the
 * Marketplace card on Home.
 * <p>
 * Search runs across product names and descriptions and shop names; the
 * filter panel narrows by price, by any of the six categories, and by the
 * area a seller works from. Opening a product shows the seller's contact
 * button. That is all it is: SheOut charges sellers a listing fee and takes
 * no part in any sale, so there is no cart and no checkout, and the foot of
 * the list says so.
 * <p>
 * Before she has searched or filtered, the screen is a place to browse, in
 * one order: what this is (and, with no shops live yet, that it is
 * opening), the six categories as big pictures, the newest listings, how
 * buying works, and last, for sellers, how to open a shop. Once she has
 * picked a category the six stay above the list as a row of pictures, so
 * switching never needs the filter sheet.
 */
export function Seller() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const goBack = useGoBack('/home');
  const filters = useDirectoryFilters();
  const { q, categories, min, max, area, mode, ask } = filters;
  const aiMode = mode === 'ai';

  const minNumber = min ? Number(min) : undefined;
  const maxNumber = max ? Number(max) : undefined;
  // A range upside down matches nothing and the server refuses it; say so
  // under the boxes and leave price out of the search until it is fixed.
  const rangeInverted = minNumber !== undefined && maxNumber !== undefined && minNumber > maxNumber;

  const fetchPage = useCallback(
    (page: number) =>
      // In AI mode the list comes from the AI search below; this one stays idle.
      aiMode ? Promise.resolve({ items: [], page: 0, pageSize: 20, totalItems: 0, totalPages: 0, hasMore: false }) :
      marketplaceApi.listings({
        page,
        pageSize: 20,
        q: q.trim() || undefined,
        category: categories,
        minPrice: rangeInverted ? undefined : minNumber,
        maxPrice: rangeInverted ? undefined : maxNumber,
        area: area.trim() || undefined,
      }),
    // categories is rebuilt every render; its joined form is the real dependency.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [aiMode, q, categories.join(','), minNumber, maxNumber, rangeInverted, area]
  );
  const list = usePagedList(fetchPage, [aiMode, q.trim(), categories.join(','), rangeInverted ? '' : `${min}-${max}`, area.trim()], {
    debounceMs: 350,
  });

  // A product code typed into the search (a customer reading it off a
  // message) opens that product straight away. Only for what she typed just
  // now - arriving back here from the product with the code still in the
  // box must not bounce her into it again.
  const typedCode = useRef(false);
  useEffect(() => {
    if (aiMode || list.loading || !typedCode.current) return;
    const code = asProductCode(q);
    if (!code) return;
    const hit = list.items.length === 1 ? list.items.find((i) => i.code === code) : undefined;
    if (hit) {
      typedCode.current = false;
      navigate(`/seller/products/${hit.productId}`);
    }
  }, [aiMode, list.loading, list.items, q, navigate]);

  // ---- search in her own words
  const [draft, setDraft] = useState(ask);
  const [smart, setSmart] = useState<{ loading: boolean; result: SmartSearchResult | null; error: string | null }>({
    loading: false,
    result: null,
    error: null,
  });
  useEffect(() => {
    if (!aiMode || !ask.trim()) {
      setSmart({ loading: false, result: null, error: null });
      return;
    }
    let live = true;
    const narrowing = {
      category: categories,
      minPrice: rangeInverted ? undefined : minNumber,
      maxPrice: rangeInverted ? undefined : maxNumber,
      area: area.trim() || undefined,
    };
    setSmart({ loading: true, result: null, error: null });
    marketplaceApi
      .askListings({ q: ask.trim(), ...narrowing })
      .then((result) => live && setSmart({ loading: false, result, error: null }))
      .catch(async () => {
        // The AI search itself could not be reached: the ordinary search
        // answers instead, so searching never simply breaks.
        try {
          const page = await marketplaceApi.listings({ q: ask.trim(), pageSize: 20, ...narrowing });
          if (live) setSmart({ loading: false, result: { mode: 'EXACT', reason: 'FAILED', items: page.items, remainingToday: -1 }, error: null });
        } catch {
          if (live) setSmart({ loading: false, result: null, error: t('seller.ask.error') });
        }
      });
    return () => {
      live = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [aiMode, ask, categories.join(','), minNumber, maxNumber, rangeInverted, area]);

  const submitAsk = (words: string) => {
    const clean = words.trim().slice(0, 200);
    setDraft(clean);
    if (clean) filters.set({ ask: clean }, true);
  };
  const switchMode = (next: 'ai' | 'exact') => {
    if (next === 'ai') {
      setDraft(ask || q);
      filters.set({ mode: 'ai', ask: '' }, true);
    } else {
      filters.set({ mode: '', ask: '', q: ask || q }, true);
    }
  };

  // What the panel is narrowing by, each removable on its own. Shown under
  // the bar so a collapsed panel never hides why the list is short.
  const chips = useMemo(() => {
    const out: { key: string; label: string; clear: () => void }[] = [];
    for (const value of categories) {
      const c = SELLER_CATEGORIES.find((x) => x.value === value)!;
      out.push({
        key: value,
        label: t(`seller.categories.${c.key}`),
        clear: () => filters.set({ cat: categories.filter((v) => v !== value).join(',') }),
      });
    }
    if ((min || max) && !rangeInverted) {
      const label = min && max
        ? t('seller.browse.priceBetween', { min: priceText(Number(min)), max: priceText(Number(max)) })
        : min
          ? t('seller.browse.priceFrom', { min: priceText(Number(min)) })
          : t('seller.browse.priceUpTo', { max: priceText(Number(max)) });
      out.push({ key: 'price', label, clear: () => filters.set({ min: '', max: '' }) });
    }
    if (area.trim()) out.push({ key: 'area', label: area.trim(), clear: () => filters.set({ area: '' }) });
    return out;
  }, [categories.join(','), min, max, area, rangeInverted, t]); // eslint-disable-line react-hooks/exhaustive-deps

  const activeFilters = chips.length;
  // In the ordinary search the picture row below shows the categories, so they are not chips as well.
  const shownChips = aiMode ? chips : chips.filter((chip) => !CATEGORY_VALUES.has(chip.key));
  const narrowed = activeFilters > 0 || q.trim().length > 0;
  /** Nothing searched or filtered yet: show the browsing layout around the list. */
  const browsing = !aiMode && !narrowed;
  const categoryOnly = categories.length > 0 && !q.trim() && !min && !max && !area.trim();
  const clearAll = () => filters.set({ q: '', cat: '', min: '', max: '', area: '', ask: '' }, true);
  const examples = [t('seller.ask.example1'), t('seller.ask.example2'), t('seller.ask.example3')];

  /** Browsing with nothing listed yet: the hero says the marketplace is opening. */
  const noShopsYet = browsing && !list.loading && !list.error && list.items.length === 0;

  /** From the picture row: just that category, or back to all if it was the only one. */
  function pickCategory(value: SellerCategory) {
    filters.set({ cat: categories.length === 1 && categories[0] === value ? '' : value }, true);
  }

  function toggleCategory(value: SellerCategory) {
    const next = categories.includes(value) ? categories.filter((v) => v !== value) : [...categories, value];
    // Kept in the fixed category order, so the URL for a selection is always the same.
    filters.set({ cat: SELLER_CATEGORIES.filter((c) => next.includes(c.value)).map((c) => c.value).join(',') });
  }

  return (
    <div className="space-y-4">
      <TopHeader variant="back" title={t('seller.marketplaceTitle')} onBack={goBack} />

      {/* Pinned while the results scroll: the search is the screen's main
          control. The filters open as a sheet, so the pinned part stays one
          row plus the active filters, never a panel over the results. */}
      <div className="sticky top-0 z-20 -mx-screen space-y-3 bg-background/95 px-screen pb-2 pt-2 backdrop-blur" data-testid="seller-search">
        {/* Two ways to search: the words as typed, or what she means. */}
        <div className="grid grid-cols-2 gap-1 rounded-full bg-primary-light p-1" role="radiogroup" aria-label={t('seller.ask.modeLabel')}>
          {(['exact', 'ai'] as const).map((m) => (
            <button
              key={m}
              type="button"
              role="radio"
              aria-checked={mode === m}
              onClick={() => mode !== m && switchMode(m)}
              className={`flex items-center justify-center gap-1.5 rounded-full px-3 py-2 text-caption font-semibold transition-colors ${
                mode === m ? 'bg-surface text-primary shadow-card' : 'text-text-secondary'
              }`}
              data-testid={`search-mode-${m}`}
            >
              {m === 'ai' && <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />}
              {m === 'ai' ? t('seller.ask.modeAi') : t('seller.ask.modeExact')}
            </button>
          ))}
        </div>
        {/* A form, so the keyboard's Search key sends an AI search; the ordinary search runs as she types. */}
        <form
          role="search"
          onSubmit={(e) => {
            e.preventDefault();
            if (aiMode) submitAsk(draft);
          }}
        >
        <ListFilterBar
          search={
            aiMode
              ? { value: draft, placeholder: t('seller.ask.placeholder'), onChange: setDraft }
              : {
                  value: q,
                  placeholder: t('seller.directory.searchPlaceholder'),
                  onChange: (value) => {
                    typedCode.current = true;
                    filters.set({ q: value });
                  },
                }
          }
          activeCount={activeFilters}
          onClearAll={() => filters.set({ cat: '', min: '', max: '', area: '' })}
          presentation="sheet"
          resultCount={aiMode || list.loading ? undefined : list.total}
        >
          <fieldset>
            <legend className="mb-2 block text-sm font-medium text-text-primary">{t('seller.browse.categories')}</legend>
            <div className="grid grid-cols-3 gap-2" data-testid="filter-categories">
              {SELLER_CATEGORIES.map((c) => {
                const selected = categories.includes(c.value);
                return (
                  <button
                    key={c.value}
                    type="button"
                    aria-pressed={selected}
                    onClick={() => toggleCategory(c.value)}
                    className={`relative flex flex-col items-center gap-1 overflow-hidden rounded-input border px-1 pb-2 pt-1 text-center transition-colors ${
                      selected ? 'border-primary bg-primary-light' : 'border-border bg-surface'
                    }`}
                    data-testid={`filter-category-${c.key}`}
                  >
                    <img src={c.art} alt="" aria-hidden="true" loading="lazy" className="h-12 w-12 object-contain" />
                    <span className={`text-caption font-semibold leading-tight ${selected ? 'text-primary' : 'text-text-primary'}`}>
                      {t(`seller.categories.${c.key}`)}
                    </span>
                    {selected && (
                      <span className="absolute right-1 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-primary text-text-inverse">
                        <Check className="h-3 w-3" strokeWidth={3} aria-hidden="true" />
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </fieldset>

          <div>
            <span className="mb-2 block text-sm font-medium text-text-primary">{t('seller.browse.priceRange')}</span>
            <div className="flex items-center gap-2">
              <div className="min-w-0 flex-1">
                <TextField
                  type="number"
                  inputMode="numeric"
                  min={0}
                  aria-label={t('seller.browse.minPrice')}
                  placeholder={t('seller.browse.min')}
                  value={min}
                  onChange={(e) => filters.set({ min: e.target.value.replace(/[^\d]/g, '') })}
                  data-testid="filter-min-price"
                />
              </div>
              <span className="shrink-0 text-sm text-text-secondary">{t('payments.to')}</span>
              <div className="min-w-0 flex-1">
                <TextField
                  type="number"
                  inputMode="numeric"
                  min={0}
                  aria-label={t('seller.browse.maxPrice')}
                  placeholder={t('seller.browse.max')}
                  value={max}
                  onChange={(e) => filters.set({ max: e.target.value.replace(/[^\d]/g, '') })}
                  data-testid="filter-max-price"
                />
              </div>
            </div>
            {rangeInverted && <p className="mt-1 text-sm text-danger" role="alert">{t('seller.browse.rangeInverted')}</p>}
          </div>

          <TextField
            label={t('seller.browse.area')}
            icon={<MapPin className="h-4 w-4 shrink-0 text-text-secondary" />}
            placeholder={t('seller.browse.areaPlaceholder')}
            value={area}
            maxLength={80}
            onChange={(e) => filters.set({ area: e.target.value })}
            data-testid="filter-area"
          />
        </ListFilterBar>
        </form>

        {aiMode && draft.trim() && draft.trim() !== ask && (
          <button
            type="button"
            onClick={() => submitAsk(draft)}
            className="flex w-full items-center justify-center gap-2 rounded-full bg-primary px-4 py-3 font-heading text-sm text-text-inverse shadow-lift"
            data-testid="ask-submit"
          >
            <Sparkles className="h-4 w-4" aria-hidden="true" /> {t('seller.ask.submit')}
          </button>
        )}

        {shownChips.length > 0 && (
          <div className="-mx-screen flex gap-2 overflow-x-auto px-screen pb-1" data-testid="active-filters">
            {shownChips.map((chip) => (
              <button
                key={chip.key}
                type="button"
                onClick={chip.clear}
                className="flex shrink-0 items-center gap-1 rounded-full bg-primary-light py-1.5 pl-3 pr-2 text-caption font-semibold text-primary"
                aria-label={t('seller.browse.removeFilter', { name: chip.label })}
              >
                {chip.label}
                <X className="h-3.5 w-3.5" aria-hidden="true" />
              </button>
            ))}
          </div>
        )}
      </div>

      {aiMode && (
        <AskResults
          ask={ask}
          state={smart}
          examples={examples}
          onExample={submitAsk}
          onExact={() => switchMode('exact')}
          onOpen={(id) => navigate(`/seller/products/${id}`)}
        />
      )}

      {browsing && (
        <>
          <MarketplaceHero opening={noShopsYet} />
          <CategoryGrid onPick={(value) => filters.set({ cat: value }, true)} />
        </>
      )}

      {/* A category picked: the six stay in reach as pictures. */}
      {!aiMode && categories.length > 0 && (
        <CategoryRail selected={categories} onPick={pickCategory} onAll={() => filters.set({ cat: '' }, true)} />
      )}

      {!aiMode && list.loading && <SkeletonList rows={4} label={t('seller.directory.loading')} />}
      {!aiMode && !list.loading && list.error && <p className="text-sm text-danger">{list.error}</p>}
      {!aiMode && !list.loading && !list.error && list.items.length === 0 && (
        categoryOnly ? (
          // Only a category was picked: that category has no shops yet, not "nothing matches".
          <ListEmptyState
            illustrated
            icon={<Store />}
            title={t('seller.discover.categoryEmptyTitle', {
              name: categories.map((v) => t(`seller.categories.${SELLER_CATEGORIES.find((c) => c.value === v)!.key}`)).join(', '),
            })}
            message={t('seller.discover.categoryEmpty')}
            action={{ label: t('seller.discover.allCategories'), onClick: clearAll }}
          />
        ) : narrowed ? (
          <ListEmptyState
            illustrated
            icon={<Store />}
            title={t('seller.directory.noMatchTitle')}
            message={t('seller.browse.noMatch')}
            action={{ label: t('seller.browse.clearAll'), onClick: clearAll }}
          />
        ) : null
      )}

      {browsing && !list.loading && list.items.length > 0 && (
        <h2 className="flex items-center gap-2 font-heading text-section text-text-primary">
          <Sparkles className="h-4 w-4 text-accent-orange" aria-hidden="true" />
          {t('seller.discover.fresh')}
        </h2>
      )}

      {!aiMode && !list.loading && list.items.length > 0 && (
        <div className="grid grid-cols-2 gap-3" data-testid="seller-listings">
          {list.items.map((item, i) => (
            <ListingTile key={item.productId} item={item} index={i} onOpen={() => navigate(`/seller/products/${item.productId}`)} />
          ))}
        </div>
      )}

      {!aiMode && <LoadMore shown={list.items.length} total={list.total} hasMore={list.hasMore} loading={list.loadingMore} onLoadMore={list.loadMore} />}

      {browsing && !list.loading && (
        <>
          <Reveal>
            <HowItWorks />
          </Reveal>
          <Reveal>
            <SellCard onOpen={() => navigate('/seller/manage')} />
          </Reveal>
        </>
      )}

      <p className="pb-2 text-center text-caption text-text-secondary">{t('seller.directory.notInvolved')}</p>
    </div>
  );
}

/**
 * The AI search's answer. Its picks are always real listings - the server
 * only returns products it showed the model - and when the model was not
 * used, the banner says the list is the ordinary keyword search instead.
 */
function AskResults({
  ask,
  state,
  examples,
  onExample,
  onExact,
  onOpen,
}: {
  ask: string;
  state: { loading: boolean; result: SmartSearchResult | null; error: string | null };
  examples: string[];
  onExample: (words: string) => void;
  onExact: () => void;
  onOpen: (productId: string) => void;
}) {
  const { t } = useTranslation();
  if (!ask.trim()) {
    return (
      <div className="space-y-3 rounded-card border border-border bg-surface p-4" data-testid="ask-intro">
        <div className="flex items-start gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary-light text-primary">
            <Sparkles className="h-5 w-5" aria-hidden="true" />
          </span>
          <div>
            <p className="font-heading text-card-title text-text-primary">{t('seller.ask.introTitle')}</p>
            <p className="mt-0.5 text-sm text-text-secondary">{t('seller.ask.introBody')}</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {examples.map((example) => (
            <button
              key={example}
              type="button"
              onClick={() => onExample(example)}
              className="rounded-full border border-border bg-background px-3 py-1.5 text-caption font-semibold text-text-primary"
            >
              {example}
            </button>
          ))}
        </div>
      </div>
    );
  }
  if (state.loading) return <SkeletonList rows={4} label={t('seller.ask.loading')} />;
  if (state.error || !state.result) return <p className="text-sm text-danger">{state.error ?? t('seller.ask.error')}</p>;

  const { mode, reason, items, remainingToday } = state.result;
  return (
    <div className="space-y-3">
      {mode === 'AI' ? (
        <p className="flex items-center gap-2 rounded-input bg-primary-light px-3 py-2 text-caption text-primary" data-testid="ask-banner" data-mode="AI">
          <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" />
          <span className="flex-1">{t('seller.ask.picked', { count: items.length, query: ask })}</span>
          {remainingToday >= 0 && <span className="shrink-0 text-text-secondary">{t('seller.ask.left', { count: remainingToday })}</span>}
        </p>
      ) : (
        <p className="rounded-input bg-accent-orange-tint px-3 py-2 text-caption text-text-primary" data-testid="ask-banner" data-mode="EXACT">
          {t(`seller.ask.fallback.${reason ?? 'FAILED'}`)}
        </p>
      )}
      {items.length === 0 ? (
        <ListEmptyState
          illustrated
          icon={<Store />}
          title={t('seller.ask.noneTitle')}
          message={t('seller.ask.none')}
          action={{ label: t('seller.ask.tryExact'), onClick: onExact }}
        />
      ) : (
        <div className="grid grid-cols-2 gap-3" data-testid="seller-listings">
          {items.map((item) => (
            <ListingTile key={item.productId} item={item} onOpen={() => onOpen(item.productId)} />
          ))}
        </div>
      )}
    </div>
  );
}

/** One product in a grid: its first photo, name, price, and whose shop it is and where. */
export function ListingTile({ item, onOpen, index = 0 }: { item: ListingCard; onOpen: () => void; index?: number }) {
  return (
    <button
      type="button"
      onClick={onOpen}
      // The first screenful arrives one after another; later pages without a wait.
      style={{ animationDelay: `${Math.min(index, 7) * 50}ms` }}
      className="flex flex-col overflow-hidden rounded-card border border-border bg-surface text-left shadow-card transition-transform duration-100 motion-safe:animate-fade-slide-in motion-safe:active:scale-[0.98]"
      data-testid="listing-tile"
    >
      <div className="flex aspect-square w-full items-center justify-center bg-primary-light">
        {item.imageUrl ? (
          <img src={item.imageUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <ImageOff className="h-8 w-8 text-text-secondary" aria-hidden="true" />
        )}
      </div>
      <div className="flex flex-1 flex-col gap-0.5 p-3">
        <p className="line-clamp-2 text-sm font-semibold text-text-primary">{item.title}</p>
        <PriceTag price={item.displayPrice} originalPrice={item.originalPrice} />
        <p className="mt-auto flex items-center gap-1 truncate text-caption text-text-secondary">
          <Store className="h-3 w-3 shrink-0" aria-hidden="true" />
          <span className="truncate">{item.businessName}</span>
        </p>
        {item.area && (
          <p className="flex items-center gap-1 text-caption text-text-secondary">
            <MapPin className="h-3 w-3 shrink-0" aria-hidden="true" />
            <span className="truncate">{item.area}</span>
          </p>
        )}
      </div>
    </button>
  );
}
