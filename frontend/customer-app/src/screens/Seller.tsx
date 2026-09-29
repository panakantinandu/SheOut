import { Check, ImageOff, MapPin, Store, X } from 'lucide-react';
import { useCallback, useMemo } from 'react';
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
import type { ListingCard, SellerCategory } from '../api/types';
import { SELLER_CATEGORIES, priceText } from '../lib/seller';

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

  const set = useCallback(
    (patch: Partial<Record<'q' | 'cat' | 'min' | 'max' | 'area', string>>) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          for (const [key, value] of Object.entries(patch)) {
            if (value) next.set(key, value);
            else next.delete(key);
          }
          return next;
        },
        { replace: true }
      );
    },
    [setParams]
  );

  return { q, categories, min, max, area, set };
}

/**
 * SheOut Marketplace: a directory of women selling from home, reached from
 * the SheOut Marketplace tile on Home.
 * <p>
 * Search runs across product names and descriptions and shop names; the
 * filter panel narrows by price, by any of the six categories, and by the
 * area a seller works from. Opening a product shows the seller's contact
 * button. That is all it is: SheOut charges sellers a listing fee and takes
 * no part in any sale, so there is no cart and no checkout, and the foot of
 * the list says so.
 * <p>
 * Running your own shop is a different errand with its own way in, "Sell on
 * SheOut" in the drawer, so this screen does not advertise it.
 */
export function Seller() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const filters = useDirectoryFilters();
  const { q, categories, min, max, area } = filters;

  const minNumber = min ? Number(min) : undefined;
  const maxNumber = max ? Number(max) : undefined;
  // A range upside down matches nothing and the server refuses it; say so
  // under the boxes and leave price out of the search until it is fixed.
  const rangeInverted = minNumber !== undefined && maxNumber !== undefined && minNumber > maxNumber;

  const fetchPage = useCallback(
    (page: number) =>
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
    [q, categories.join(','), minNumber, maxNumber, rangeInverted, area]
  );
  const list = usePagedList(fetchPage, [q.trim(), categories.join(','), rangeInverted ? '' : `${min}-${max}`, area.trim()], {
    debounceMs: 350,
  });

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
  const narrowed = activeFilters > 0 || q.trim().length > 0;
  const clearAll = () => filters.set({ q: '', cat: '', min: '', max: '', area: '' });

  function toggleCategory(value: SellerCategory) {
    const next = categories.includes(value) ? categories.filter((v) => v !== value) : [...categories, value];
    // Kept in the fixed category order, so the URL for a selection is always the same.
    filters.set({ cat: SELLER_CATEGORIES.filter((c) => next.includes(c.value)).map((c) => c.value).join(',') });
  }

  return (
    <div className="space-y-4">
      <TopHeader variant="back" title={t('seller.marketplaceTitle')} onBack={() => navigate(-1)} />

      {/* Pinned while the results scroll: the search is the screen's main
          control. The filters open as a sheet, so the pinned part stays one
          row plus the active filters, never a panel over the results. */}
      <div className="sticky top-0 z-20 -mx-screen space-y-3 bg-background/95 px-screen pb-2 pt-2 backdrop-blur" data-testid="seller-search">
        <ListFilterBar
          search={{ value: q, placeholder: t('seller.directory.searchPlaceholder'), onChange: (value) => filters.set({ q: value }) }}
          activeCount={activeFilters}
          onClearAll={() => filters.set({ cat: '', min: '', max: '', area: '' })}
          presentation="sheet"
          resultCount={list.loading ? undefined : list.total}
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

        {chips.length > 0 && (
          <div className="-mx-screen flex gap-2 overflow-x-auto px-screen pb-1" data-testid="active-filters">
            {chips.map((chip) => (
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

      {list.loading && <SkeletonList rows={4} label={t('seller.directory.loading')} />}
      {!list.loading && list.error && <p className="text-sm text-danger">{list.error}</p>}
      {!list.loading && !list.error && list.items.length === 0 && (
        narrowed ? (
          <ListEmptyState
            illustrated
            icon={<Store />}
            title={t('seller.directory.noMatchTitle')}
            message={t('seller.browse.noMatch')}
            action={{ label: t('seller.browse.clearAll'), onClick: clearAll }}
          />
        ) : (
          <ListEmptyState illustrated icon={<Store />} title={t('seller.directory.emptyTitle')} message={t('seller.directory.empty')} />
        )
      )}

      {!list.loading && list.items.length > 0 && (
        <div className="grid grid-cols-2 gap-3" data-testid="seller-listings">
          {list.items.map((item) => (
            <ListingTile key={item.productId} item={item} onOpen={() => navigate(`/seller/products/${item.productId}`)} />
          ))}
        </div>
      )}

      <LoadMore shown={list.items.length} total={list.total} hasMore={list.hasMore} loading={list.loadingMore} onLoadMore={list.loadMore} />

      <p className="pb-2 text-center text-caption text-text-secondary">{t('seller.directory.notInvolved')}</p>
    </div>
  );
}

/** One product in a grid: its first photo, name, price, and whose shop it is and where. */
export function ListingTile({ item, onOpen }: { item: ListingCard; onOpen: () => void }) {
  return (
    <button
      type="button"
      onClick={onOpen}
      className="flex flex-col overflow-hidden rounded-card border border-border bg-surface text-left shadow-card transition-transform duration-100 motion-safe:active:scale-[0.98]"
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
        <p className="font-heading text-card-title text-primary">{priceText(item.displayPrice)}</p>
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
