import { ImageOff, Store } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  IconCircle,
  ListEmptyState,
  ListFilterBar,
  LoadMore,
  SelectField,
  SkeletonList,
  TopHeader,
  usePagedList,
  useTranslation,
} from '@sheout/design-system';
import { marketplaceApi } from '../api/client';
import type { ListingCard, SellerCategory, SellerShop } from '../api/types';
import { useAppDrawer } from '../components/AppDrawer';
import { SELLER_CATEGORIES, categoryKey, priceText } from '../lib/seller';

/**
 * SheOut Seller: a directory of women selling from home.
 * <p>
 * Browse by the six categories or search, open a product, and contact the
 * seller on WhatsApp or by phone. That is all it is: SheOut charges sellers
 * a listing fee and takes no part in any sale, so there is no cart and no
 * checkout here, and the screen says so rather than letting anyone expect
 * one.
 */
export function Seller() {
  const { t } = useTranslation();
  const drawer = useAppDrawer();
  const navigate = useNavigate();
  const [category, setCategory] = useState<SellerCategory | ''>('');
  const [query, setQuery] = useState('');
  const [shop, setShop] = useState<SellerShop | null | undefined>(undefined);

  useEffect(() => {
    marketplaceApi.myShop().then(setShop).catch(() => setShop(null));
  }, []);

  const fetchPage = useCallback(
    (page: number) => marketplaceApi.listings({ page, pageSize: 20, category: category || undefined, q: query.trim() || undefined }),
    [category, query]
  );
  const list = usePagedList(fetchPage, [category, query], { debounceMs: 350 });
  const activeFilters = (category ? 1 : 0) + (query.trim() ? 1 : 0);

  return (
    <div className="space-y-6">
      <TopHeader variant="plain" title={t('seller.title')} onMenuClick={drawer.open} />

      <Card variant="primary" className="relative overflow-hidden p-5" data-testid="seller-hero">
        <span className="pointer-events-none absolute -right-16 -top-16 h-48 w-48 rounded-full bg-white/10" aria-hidden="true" />
        <div className="relative flex items-start gap-3">
          <IconCircle size="sm" icon={<Store />} className="bg-white/15 text-white" />
          <div className="min-w-0 flex-1">
            <h1 className="font-heading text-title">{t('seller.directory.headline')}</h1>
            <p className="mt-1 text-sm opacity-90">{t('seller.directory.subhead')}</p>
          </div>
        </div>
        <div className="relative mt-4">
          <Button
            size="md"
            variant="secondary"
            className="border-transparent text-primary"
            onClick={() => navigate('/seller/manage')}
            data-testid="sell-on-sheout"
          >
            {shop ? t('seller.directory.manageShop') : t('seller.directory.sellOnSheOut')}
          </Button>
        </div>
      </Card>

      <section className="space-y-3">
        <h2 className="font-heading text-section text-text-primary">{t('seller.categoriesTitle')}</h2>
        <div className="grid grid-cols-3 gap-3" data-testid="seller-categories">
          {SELLER_CATEGORIES.map((c) => {
            const selected = category === c.value;
            return (
              <button
                key={c.value}
                type="button"
                aria-pressed={selected}
                onClick={() => setCategory(selected ? '' : c.value)}
                className={`overflow-hidden rounded-card border text-left transition-shadow ${selected ? 'border-primary shadow-lift ring-2 ring-primary' : 'border-border bg-surface'}`}
                data-testid={`seller-category-${c.key}`}
              >
                <div className={`flex h-20 items-center justify-center ${c.tint}`}>
                  <img src={c.art} alt="" aria-hidden="true" loading="lazy" className="h-16 w-16 object-contain drop-shadow-md" />
                </div>
                <p className="px-2 py-2 text-center text-caption font-semibold text-text-primary">{t(`seller.categories.${c.key}`)}</p>
              </button>
            );
          })}
        </div>
      </section>

      <ListFilterBar
        search={{ value: query, placeholder: t('seller.directory.searchPlaceholder'), onChange: setQuery }}
        activeCount={activeFilters}
        onClearAll={() => {
          setCategory('');
          setQuery('');
        }}
      >
        <SelectField
          label={t('seller.directory.category')}
          placeholder={t('seller.directory.anyCategory')}
          value={category}
          onChange={(e) => setCategory(e.target.value as SellerCategory | '')}
          options={SELLER_CATEGORIES.map((c) => ({ value: c.value, label: t(`seller.categories.${c.key}`) }))}
        />
      </ListFilterBar>

      {list.loading && <SkeletonList rows={4} label={t('seller.directory.loading')} />}
      {!list.loading && list.error && <p className="text-sm text-danger">{list.error}</p>}
      {!list.loading && !list.error && list.items.length === 0 && (
        activeFilters > 0 ? (
          <ListEmptyState icon={<Store />} title={t('seller.directory.noMatchTitle')} message={t('seller.directory.noMatch')} />
        ) : (
          <ListEmptyState icon={<Store />} title={t('seller.directory.emptyTitle')} message={t('seller.directory.empty')} />
        )
      )}

      <div className="grid grid-cols-2 gap-3" data-testid="seller-listings">
        {list.items.map((item) => (
          <ListingTile key={item.productId} item={item} onOpen={() => navigate(`/seller/products/${item.productId}`)} />
        ))}
      </div>

      <LoadMore shown={list.items.length} total={list.total} hasMore={list.hasMore} loading={list.loadingMore} onLoadMore={list.loadMore} />

      <p className="pb-2 text-center text-caption text-text-secondary">{t('seller.directory.notInvolved')}</p>
    </div>
  );
}

export function ListingTile({ item, onOpen }: { item: ListingCard; onOpen: () => void }) {
  const { t } = useTranslation();
  return (
    <button
      type="button"
      onClick={onOpen}
      className="overflow-hidden rounded-card border border-border bg-surface text-left shadow-card"
      data-testid="listing-tile"
    >
      <div className="flex aspect-square items-center justify-center bg-primary-light">
        {item.imageUrl ? (
          <img src={item.imageUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <ImageOff className="h-8 w-8 text-text-secondary" aria-hidden="true" />
        )}
      </div>
      <div className="space-y-0.5 p-3">
        <p className="line-clamp-2 text-sm font-semibold text-text-primary">{item.title}</p>
        <p className="font-heading text-card-title text-primary">{priceText(item.displayPrice)}</p>
        <p className="truncate text-caption text-text-secondary">
          {item.businessName} · {t(`seller.categories.${categoryKey(item.category)}`)}
        </p>
      </div>
    </button>
  );
}
