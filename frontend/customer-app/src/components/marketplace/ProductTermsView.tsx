import { Box, CalendarClock, Home, Info, Palette, RotateCcw, Store, Truck } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from '@sheout/design-system';
import type { Fulfilment, ProductAvailability, ProductTerms } from '../../api/types';

/** In stock, per piece, nothing else said - what a product saved before these existed means. */
export const DEFAULT_TERMS: ProductTerms = {
  availability: 'IN_STOCK',
  quantityAvailable: null,
  readyInDays: null,
  priceUnit: 'PIECE',
  minOrderQuantity: null,
  options: null,
  fulfilment: [],
  deliveryNote: null,
  returnPolicy: null,
};

/** "Only 3 left" from here down - enough to be useful, not enough to nag. */
const FEW_LEFT = 5;

const FULFILMENT_ICON: Record<Fulfilment, ReactNode> = {
  HOME_DELIVERY: <Truck className="h-4 w-4" aria-hidden="true" />,
  PICKUP: <Store className="h-4 w-4" aria-hidden="true" />,
  AT_YOUR_HOME: <Home className="h-4 w-4" aria-hidden="true" />,
  AT_SELLER_PLACE: <Store className="h-4 w-4" aria-hidden="true" />,
};

/**
 * Whether she can have it, in one coloured word: green in stock (amber when
 * only a few are left), blue made to order, grey out of stock.
 */
export function AvailabilityPill({ terms }: { terms: ProductTerms }) {
  const { t } = useTranslation();
  const { availability, quantityAvailable, readyInDays } = terms;
  const few = availability === 'IN_STOCK' && quantityAvailable != null && quantityAvailable <= FEW_LEFT;
  const text =
    availability === 'OUT_OF_STOCK'
      ? t('seller.terms.availability.OUT_OF_STOCK')
      : availability === 'MADE_TO_ORDER'
        ? readyInDays != null
          ? t('seller.terms.readyIn', { count: readyInDays })
          : t('seller.terms.availability.MADE_TO_ORDER')
        : few
          ? t('seller.terms.onlyLeft', { count: quantityAvailable! })
          : quantityAvailable != null
            ? t('seller.terms.inStockCount', { count: quantityAvailable })
            : t('seller.terms.availability.IN_STOCK');
  const tone =
    availability === 'OUT_OF_STOCK'
      ? 'bg-background text-text-secondary ring-border'
      : availability === 'MADE_TO_ORDER'
        ? 'bg-accent-blue-tint text-accent-blue-strong ring-accent-blue/20'
        : few
          ? 'bg-accent-orange-tint text-accent-orange-strong ring-accent-orange/25'
          : 'bg-accent-green-tint text-accent-green-strong ring-accent-green/25';
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold ring-1 ${tone}`} data-testid="product-availability">
      <span
        className={`h-2 w-2 rounded-full ${availability === 'OUT_OF_STOCK' ? 'bg-text-secondary' : availability === 'MADE_TO_ORDER' ? 'bg-accent-blue' : few ? 'bg-accent-orange' : 'bg-accent-green'}`}
        aria-hidden="true"
      />
      {text}
    </span>
  );
}

/** The small corner label on a grid tile - only for the two states a buyer should know before opening. */
export function AvailabilityBadge({ availability }: { availability?: ProductAvailability }) {
  const { t } = useTranslation();
  if (availability !== 'OUT_OF_STOCK' && availability !== 'MADE_TO_ORDER') return null;
  return (
    <span
      className={`absolute left-2 top-2 rounded-full px-2 py-0.5 text-micro font-bold shadow-lift ${availability === 'OUT_OF_STOCK' ? 'bg-text-primary text-text-inverse' : 'bg-surface/95 text-accent-blue-strong'}`}
      data-testid="tile-availability"
    >
      {t(`seller.terms.availability.${availability}`)}
    </span>
  );
}

/**
 * The rest of what a buyer asks before calling, as rows with an icon each:
 * the sizes or colours, the minimum, how it reaches her, delivery details,
 * returns. A row she did not fill in is left out, not shown as "not given".
 */
export function ProductTermsCard({ terms }: { terms: ProductTerms }) {
  const { t } = useTranslation();
  const options = terms.options ? terms.options.split(',').map((o) => o.trim()).filter(Boolean) : [];
  const rows: { key: string; icon: ReactNode; label: string; value: ReactNode }[] = [];
  if (options.length) {
    rows.push({
      key: 'options',
      icon: <Palette className="h-4 w-4" aria-hidden="true" />,
      label: t('seller.terms.options'),
      value: (
        <span className="flex flex-wrap gap-1.5" data-testid="product-options">
          {options.map((o) => (
            <span key={o} className="rounded-full border border-border bg-background px-2.5 py-0.5 text-sm text-text-primary">{o}</span>
          ))}
        </span>
      ),
    });
  }
  if (terms.minOrderQuantity != null) {
    rows.push({
      key: 'min',
      icon: <Box className="h-4 w-4" aria-hidden="true" />,
      label: t('seller.terms.minOrder'),
      value: t('seller.terms.minOrderValue', { count: terms.minOrderQuantity, unit: t(`seller.terms.unitName.${terms.priceUnit}`).toLowerCase() }),
    });
  }
  if (terms.availability === 'MADE_TO_ORDER' && terms.readyInDays != null) {
    rows.push({
      key: 'ready',
      icon: <CalendarClock className="h-4 w-4" aria-hidden="true" />,
      label: t('seller.terms.readyTitle'),
      value: t('seller.terms.readyValue', { count: terms.readyInDays }),
    });
  }
  if (terms.fulfilment.length) {
    rows.push({
      key: 'fulfilment',
      icon: <Truck className="h-4 w-4" aria-hidden="true" />,
      label: t('seller.terms.fulfilmentTitle'),
      value: (
        <span className="flex flex-col gap-1" data-testid="product-fulfilment">
          {terms.fulfilment.map((f) => (
            <span key={f} className="flex items-center gap-1.5 text-text-primary">
              <span className="text-primary">{FULFILMENT_ICON[f]}</span>
              {t(`seller.terms.fulfilment.${f}`)}
            </span>
          ))}
        </span>
      ),
    });
  }
  if (terms.deliveryNote) {
    rows.push({
      key: 'delivery',
      icon: <Info className="h-4 w-4" aria-hidden="true" />,
      label: t('seller.terms.deliveryTitle'),
      value: <span className="whitespace-pre-wrap">{terms.deliveryNote}</span>,
    });
  }
  if (terms.returnPolicy) {
    rows.push({
      key: 'returns',
      icon: <RotateCcw className="h-4 w-4" aria-hidden="true" />,
      label: t('seller.terms.returnsTitle'),
      value: t(`seller.terms.returns.${terms.returnPolicy}`),
    });
  }
  if (!rows.length) return null;
  return (
    <section className="rounded-card border border-border bg-surface p-4 shadow-card" data-testid="product-terms">
      <h2 className="mb-3 font-heading text-card-title text-text-primary">{t('seller.terms.title')}</h2>
      <dl className="divide-y divide-border">
        {rows.map((row) => (
          <div key={row.key} className="flex gap-3 py-2.5 first:pt-0 last:pb-0">
            <dt className="flex w-32 shrink-0 items-start gap-2 text-sm text-text-secondary">
              <span className="mt-0.5 text-text-secondary">{row.icon}</span>
              {row.label}
            </dt>
            <dd className="min-w-0 flex-1 text-sm text-text-primary">{row.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

/** "Updated today", "Updated yesterday", "Updated 4 days ago", then a date. */
export function updatedText(iso: string, t: (key: string, options?: Record<string, unknown>) => string): string {
  const then = new Date(iso);
  const days = Math.floor((Date.now() - then.getTime()) / 86_400_000);
  if (days <= 0) return t('seller.terms.updatedToday');
  if (days === 1) return t('seller.terms.updatedYesterday');
  if (days < 30) return t('seller.terms.updatedDays', { count: days });
  return t('seller.terms.updatedOn', { date: then.toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' }) });
}
