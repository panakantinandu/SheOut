import { AmountText } from '@sheout/design-system';
import { useTranslation } from '@sheout/design-system';

/**
 * The fare and what a promotion took off it, shown side by side: the real
 * fare stays visible, so she can see what the offer was worth and what a
 * trip will cost once it is used up.
 */
export function PromoFareLines({ fare, discount, youPay, promotionName }: {
  fare: number;
  discount: number;
  youPay: number;
  promotionName: string | null;
}) {
  const { t } = useTranslation();
  return (
    <div className="space-y-1 text-sm" data-testid="promo-fare-lines">
      <div className="flex justify-between text-text-secondary">
        <span>{t('promo.fare')}</span>
        <span data-testid="promo-fare" className="line-through">
          <AmountText amount={fare} exact />
        </span>
      </div>
      <div className="flex justify-between text-success">
        <span>{promotionName ?? t('promo.offer')}</span>
        <span data-testid="promo-discount">−₹{discount.toFixed(2)}</span>
      </div>
      <div className="flex justify-between font-semibold text-text-primary">
        <span>{youPay === 0 ? t('promo.youPayCovered') : t('promo.youPayApplied')}</span>
        <span data-testid="promo-you-pay"><AmountText amount={youPay} exact /></span>
      </div>
    </div>
  );
}
