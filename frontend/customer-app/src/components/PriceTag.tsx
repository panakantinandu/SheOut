import { useTranslation } from '@sheout/design-system';
import { discountPercent, priceText } from '../lib/seller';

/**
 * A marketplace price, and the discount when there is one: the earlier
 * price struck through above it, with the percentage off worked out here
 * from the two numbers rather than typed by the seller.
 * <p>
 * Only a display. Nothing is ever paid in the app for a marketplace
 * product - Contact Seller is the only way to buy.
 */
export function PriceTag({
  price,
  originalPrice,
  size = 'md',
}: {
  price: number;
  originalPrice?: number | null;
  size?: 'md' | 'lg';
}) {
  const { t } = useTranslation();
  const off = discountPercent(price, originalPrice);
  return (
    <div data-testid="price-tag">
      {off !== null && (
        <p className={`flex flex-wrap items-center gap-x-2 gap-y-0.5 ${size === 'lg' ? 'text-body' : 'text-caption'}`}>
          <s className="text-text-secondary" data-testid="original-price" aria-label={t('seller.price.was', { price: priceText(originalPrice!) })}>
            {priceText(originalPrice!)}
          </s>
          <span className="rounded-full bg-accent-green-tint px-2 py-0.5 text-micro font-bold text-accent-green-strong" data-testid="percent-off">
            {t('seller.price.off', { percent: off })}
          </span>
        </p>
      )}
      <p className={size === 'lg' ? 'font-heading text-display text-primary' : 'font-heading text-card-title text-primary'} data-testid="product-price">
        {priceText(price)}
      </p>
    </div>
  );
}
