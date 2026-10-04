import { Copy, EyeOff, ImageOff, Minus, Package, PackagePlus, Pencil, Plus, Search } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Card, ListEmptyState, SkeletonCard, TextField, TopHeader, showToast, useTranslation } from '@sheout/design-system';
import { marketplaceApi } from '../api/client';
import type { ProductInput, ProductTerms, SellerProduct, SellerShop } from '../api/types';
import { AvailabilityPill, DEFAULT_TERMS, FEW_LEFT } from '../components/marketplace/ProductTermsView';
import { apiErrorText } from '../lib/apiErrors';
import { priceText } from '../lib/seller';
import { useGoBack } from '../lib/useGoBack';

type Filter = 'all' | 'inStock' | 'low' | 'out' | 'hidden';

/** "#mcsq 87k" -> "MCSQ87K": how a buyer reads a code out, cleaned to how it is printed. */
function asCode(text: string): string {
  return text.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

const termsOf = (p: SellerProduct): ProductTerms => p.terms ?? DEFAULT_TERMS;
const isLow = (p: SellerProduct) => {
  const terms = termsOf(p);
  return terms.availability === 'IN_STOCK' && terms.quantityAvailable != null && terms.quantityAvailable <= FEW_LEFT;
};
const isOut = (p: SellerProduct) => termsOf(p).availability === 'OUT_OF_STOCK';

/** The product as it is, with only what she changed - the rest goes back exactly as it was. */
function asInput(p: SellerProduct, change: Partial<ProductInput>): ProductInput {
  const terms = termsOf(p);
  return {
    title: p.title,
    description: p.description,
    displayPrice: p.displayPrice,
    originalPrice: p.originalPrice,
    active: p.active,
    availability: terms.availability,
    quantityAvailable: terms.quantityAvailable,
    readyInDays: terms.readyInDays,
    priceUnit: terms.priceUnit,
    minOrderQuantity: terms.minOrderQuantity,
    options: terms.options,
    fulfilment: terms.fulfilment,
    deliveryNote: terms.deliveryNote,
    returnPolicy: terms.returnPolicy,
    ...change,
  };
}

/**
 * A seller's products on their own, the way she runs them day to day: find
 * one by the code a buyer quoted, see what is running low or sold out at a
 * glance, and change its stock or hide it in one tap - without opening the
 * shop, its plan and its details every time.
 * <p>
 * Reached straight from the menu and Profile ("My products") once she has a
 * shop. The shop screen keeps her plan, her details and her pickup point.
 */
export function MyProducts() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const goBack = useGoBack('/profile');
  const [shop, setShop] = useState<SellerShop | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  // Opened from a reminder ("3 buyers asked about ..."): straight to that product.
  const [params] = useSearchParams();
  const [find, setFind] = useState(params.get('code') ?? '');
  const [filter, setFilter] = useState<Filter>('all');
  const [busyId, setBusyId] = useState<string | null>(null);

  useEffect(() => {
    marketplaceApi
      .myShop()
      .then((s) => {
        // No shop yet: the place to start one.
        if (!s) navigate('/seller/manage', { replace: true });
        else setShop(s);
      })
      .catch((err) => setError(apiErrorText(err, 'seller.shop.loadError')));
  }, [navigate]);

  const products = shop?.products ?? [];
  const counts = useMemo(
    () => ({
      all: products.length,
      inStock: products.filter((p) => p.active && !isOut(p) && !isLow(p)).length,
      low: products.filter((p) => p.active && isLow(p)).length,
      out: products.filter((p) => p.active && isOut(p)).length,
      hidden: products.filter((p) => !p.active).length,
    }),
    [products]
  );

  const code = asCode(find);
  const words = find.trim().toLowerCase();
  const exact = code.length === 7 ? products.find((p) => p.code === code) : undefined;
  const shown = exact
    ? [exact]
    : products
        .filter((p) => !words || (code.length >= 3 && p.code.includes(code)) || p.title.toLowerCase().includes(words))
        .filter((p) =>
          filter === 'all' ? true
            : filter === 'hidden' ? !p.active
              : !p.active ? false
                : filter === 'low' ? isLow(p)
                  : filter === 'out' ? isOut(p)
                    : !isOut(p) && !isLow(p)
        );

  const canEdit = Boolean(shop?.canEdit);
  const live = shop?.status === 'ACTIVE';

  async function change(p: SellerProduct, patch: Partial<ProductInput>, done: string) {
    setBusyId(p.id);
    try {
      setShop(await marketplaceApi.updateProduct(p.id, asInput(p, patch)));
      showToast(done);
    } catch (err) {
      showToast(apiErrorText(err, 'seller.shop.actionError'));
    } finally {
      setBusyId(null);
    }
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      showToast(t('seller.shop.codeCopied', { code: text }));
    } catch {
      // The code is on screen to read out.
    }
  }

  const chips: { key: Filter; label: string; tone: string }[] = [
    { key: 'all', label: t('seller.myProducts.filter.all', { count: counts.all }), tone: '' },
    { key: 'inStock', label: t('seller.myProducts.filter.inStock', { count: counts.inStock }), tone: '' },
    { key: 'low', label: t('seller.myProducts.filter.low', { count: counts.low }), tone: counts.low ? 'text-accent-orange-strong' : '' },
    { key: 'out', label: t('seller.myProducts.filter.out', { count: counts.out }), tone: counts.out ? 'text-danger' : '' },
    { key: 'hidden', label: t('seller.myProducts.filter.hidden', { count: counts.hidden }), tone: '' },
  ];

  return (
    <div className="space-y-4 pb-6" data-testid="my-products">
      <TopHeader variant="back" title={t('seller.myProducts.title')} onBack={goBack} />

      {error && <p className="text-sm text-danger">{error}</p>}
      {shop === undefined && !error && <SkeletonCard lines={4} label={t('seller.shop.loading')} />}

      {shop && (
        <>
          {/* The code first: a buyer on the phone says "MCSQ87K" and she finds it here. */}
          <div className="relative">
            <TextField
              label={t('seller.myProducts.findLabel')}
              placeholder={t('seller.shop.findPlaceholder')}
              value={find}
              onChange={(e) => setFind(e.target.value)}
              autoCapitalize="characters"
              autoComplete="off"
              icon={<Search className="h-4 w-4" />}
              data-testid="find-product"
            />
            {words && (
              <p className="mt-1 text-caption text-text-secondary" data-testid="find-result">
                {exact
                  ? t('seller.myProducts.found', { code: exact.code })
                  : shown.length === 0
                    ? t('seller.shop.findNone')
                    : t('seller.shop.findCount', { count: shown.length })}
              </p>
            )}
          </div>

          {!exact && products.length > 0 && (
            <div className="-mx-screen flex gap-2 overflow-x-auto px-screen pb-1" role="tablist" data-testid="stock-filters">
              {chips.map((c) => (
                <button
                  key={c.key}
                  type="button"
                  role="tab"
                  aria-selected={filter === c.key}
                  onClick={() => setFilter(c.key)}
                  className={`shrink-0 rounded-full border px-3.5 py-1.5 text-sm font-semibold ${
                    filter === c.key ? 'border-primary bg-primary text-text-inverse' : `border-border bg-surface ${c.tone || 'text-text-primary'}`
                  }`}
                  data-testid={`filter-${c.key}`}
                >
                  {c.label}
                </button>
              ))}
            </div>
          )}

          {!canEdit && products.length > 0 && <p className="rounded-input bg-background px-4 py-3 text-sm text-text-secondary">{t('seller.myProducts.readOnly')}</p>}

          {products.length === 0 ? (
            <ListEmptyState icon={<PackagePlus />} title={t('seller.myProducts.emptyTitle')} message={t('seller.myProducts.emptyBody')} />
          ) : shown.length === 0 ? (
            <p className="py-6 text-center text-sm text-text-secondary">{words ? t('seller.shop.findNone') : t('seller.myProducts.noneInFilter')}</p>
          ) : (
            <div className="space-y-3">
              {shown.map((p) => {
                const terms = termsOf(p);
                const busy = busyId === p.id;
                const counted = terms.availability === 'IN_STOCK' && terms.quantityAvailable != null;
                return (
                  <Card key={p.id} className={`space-y-3 ${exact ? 'ring-2 ring-primary/30' : ''}`} data-testid="my-product">
                    <div className="flex gap-3">
                      <span className="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-input bg-primary-light">
                        {p.images[0]?.url ? (
                          <img src={p.images[0].url} alt="" className={`h-full w-full object-cover ${!p.active || isOut(p) ? 'opacity-60' : ''}`} />
                        ) : (
                          <ImageOff className="h-6 w-6 text-text-secondary" aria-hidden="true" />
                        )}
                      </span>
                      <div className="min-w-0 flex-1 space-y-1">
                        <div className="flex items-start justify-between gap-2">
                          <p className="line-clamp-2 font-semibold text-text-primary">{p.title}</p>
                          <button
                            type="button"
                            onClick={() => void copy(p.code)}
                            className="flex shrink-0 items-center gap-1 rounded-full bg-background px-2 py-0.5 font-mono text-xs font-semibold tracking-wider text-text-primary"
                            aria-label={t('seller.shop.copyCode', { code: p.code })}
                            data-testid="product-code"
                          >
                            {p.code}
                            <Copy className="h-3 w-3 text-text-secondary" aria-hidden="true" />
                          </button>
                        </div>
                        <p className="text-sm font-semibold text-primary">
                          {p.originalPrice != null && p.originalPrice > p.displayPrice && (
                            <s className="mr-1.5 font-normal text-text-secondary">{priceText(p.originalPrice)}</s>
                          )}
                          {priceText(p.displayPrice)}
                        </p>
                        <div className="flex flex-wrap items-center gap-1.5">
                          <AvailabilityPill terms={terms} />
                          {!p.active && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-background px-2.5 py-1 text-xs font-semibold text-text-secondary">
                              <EyeOff className="h-3 w-3" aria-hidden="true" /> {t('seller.myProducts.hiddenBadge')}
                            </span>
                          )}
                          {p.images.length === 0 && <span className="text-xs font-semibold text-accent-orange-strong">{t('seller.wizard.products.missingPhoto')}</span>}
                          {(p.enquiriesToday ?? 0) > 0 && (
                            <span className="rounded-full bg-primary-light px-2.5 py-1 text-xs font-semibold text-primary" data-testid="enquiries-today">
                              {t('seller.myProducts.askedToday', { count: p.enquiriesToday })}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>

                    {canEdit && (
                      <div className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
                        {/* How many left, one tap each way. Down to 0 is sold out. */}
                        {counted && (
                          <div className="flex items-center rounded-full border border-border" data-testid="stock-stepper">
                            <button
                              type="button"
                              className="flex h-9 w-9 items-center justify-center text-text-primary disabled:opacity-40"
                              disabled={busy}
                              aria-label={t('seller.myProducts.oneLess')}
                              onClick={() =>
                                void change(
                                  p,
                                  terms.quantityAvailable! <= 1
                                    ? { availability: 'OUT_OF_STOCK', quantityAvailable: null }
                                    : { quantityAvailable: terms.quantityAvailable! - 1 },
                                  terms.quantityAvailable! <= 1 ? t('seller.myProducts.markedOut') : t('seller.myProducts.stockNow', { count: terms.quantityAvailable! - 1 })
                                )
                              }
                            >
                              <Minus className="h-4 w-4" />
                            </button>
                            <span className="min-w-[2rem] text-center text-sm font-semibold text-text-primary">{terms.quantityAvailable}</span>
                            <button
                              type="button"
                              className="flex h-9 w-9 items-center justify-center text-text-primary disabled:opacity-40"
                              disabled={busy || terms.quantityAvailable! >= 9999}
                              aria-label={t('seller.myProducts.oneMore')}
                              onClick={() =>
                                void change(p, { quantityAvailable: terms.quantityAvailable! + 1 }, t('seller.myProducts.stockNow', { count: terms.quantityAvailable! + 1 }))
                              }
                            >
                              <Plus className="h-4 w-4" />
                            </button>
                          </div>
                        )}
                        {isOut(p) ? (
                          <Button size="md" variant="secondary" disabled={busy} onClick={() => void change(p, { availability: 'IN_STOCK', quantityAvailable: null }, t('seller.myProducts.markedIn'))} data-testid="mark-in-stock">
                            {t('seller.myProducts.backInStock')}
                          </Button>
                        ) : (
                          <Button size="md" variant="secondary" disabled={busy} onClick={() => void change(p, { availability: 'OUT_OF_STOCK', quantityAvailable: null }, t('seller.myProducts.markedOut'))} data-testid="mark-out-of-stock">
                            {t('seller.myProducts.markOut')}
                          </Button>
                        )}
                        <Button size="md" variant="secondary" disabled={busy} onClick={() => void change(p, { active: !p.active }, p.active ? t('seller.myProducts.nowHidden') : t('seller.myProducts.nowShown'))} data-testid="toggle-hidden">
                          {p.active ? t('seller.myProducts.hide') : t('seller.myProducts.show')}
                        </Button>
                        <div className="ml-auto flex gap-2">
                          {live && p.active && (
                            <button
                              type="button"
                              className="flex h-9 w-9 items-center justify-center rounded-full border border-border text-primary"
                              aria-label={t('seller.shop.send')}
                              onClick={() =>
                                navigate('/book/parcel', {
                                  state: {
                                    marketplace: 'seller',
                                    productId: p.id,
                                    pickup: shop.parcelPickup ?? undefined,
                                    parcelNote: t('seller.parcel.note', { code: p.code, title: p.title }).slice(0, 300),
                                  },
                                })
                              }
                              data-testid="send-parcel"
                            >
                              <Package className="h-4 w-4" />
                            </button>
                          )}
                          <button
                            type="button"
                            className="flex h-9 w-9 items-center justify-center rounded-full border border-border text-primary"
                            aria-label={t('seller.shop.edit')}
                            onClick={() => navigate(`/seller/manage/products/${p.id}`, { state: { fromShop: true } })}
                            data-testid="edit-product"
                          >
                            <Pencil className="h-4 w-4" />
                          </button>
                        </div>
                      </div>
                    )}
                  </Card>
                );
              })}
            </div>
          )}

          {canEdit && products.length < shop.maxProducts && (
            <Button variant="secondary" fullWidth icon={<PackagePlus className="h-4 w-4" />} onClick={() => navigate('/seller/manage/products/new', { state: { fromShop: true } })} data-testid="add-product">
              {t('seller.shop.addProduct')}
            </Button>
          )}
        </>
      )}
    </div>
  );
}
