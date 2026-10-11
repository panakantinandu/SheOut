import { ShieldCheck, Wallet } from 'lucide-react';
import { ServiceArt, type ServiceArtKind } from '@sheout/design-system';
import type { FareQuoteState } from '../lib/useFareQuote';
import { PromoFareLines } from './PromoFareLines';
import { useTranslation } from '@sheout/design-system';

/**
 * What the booking sheet shows when pulled up: only what the summary does not
 * already say. The fare, the distance and the times are in the summary and
 * are not repeated here (the sheet used to open on a second card with the
 * same fare and km). New here: how the promotion was worked out, how she
 * pays, what "fixed price" means, and who is nearby.
 */
export function TripSheetDetails({
  fare,
  nearbyCount,
  kind,
}: {
  fare: FareQuoteState;
  nearbyCount: number;
  kind: ServiceArtKind;
}) {
  const { t } = useTranslation();
  const promo = fare.quote && fare.quote.promoDiscount > 0 ? fare.quote : null;
  return (
    <>
      {promo && (
        <PromoFareLines fare={promo.fareEstimate} discount={promo.promoDiscount} youPay={promo.youPay} promotionName={promo.promotionName} />
      )}
      <p className="flex items-start gap-2 text-xs text-text-secondary" data-testid="sheet-pay-after">
        <Wallet className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        {t('booking.payAfterTrip')}
      </p>
      <p className="flex items-start gap-2 text-xs text-text-secondary">
        <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        {t('booking.fixedPriceNote')}
      </p>
      {nearbyCount > 0 && (
        <p className="flex items-center gap-2 text-xs font-medium text-primary" data-testid="nearby-count">
          <ServiceArt kind={kind} size="xs" />
          {t('booking.nearbyCount', { count: nearbyCount })}
        </p>
      )}
    </>
  );
}
