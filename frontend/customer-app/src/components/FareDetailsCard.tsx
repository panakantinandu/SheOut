import { useEffect, useState } from 'react';
import { AmountText, Card, useTranslation } from '@sheout/design-system';
import { getFareDetails } from '../api/client';
import type { FareDetails } from '../api/types';

/** "×1.3", for a multiplier that changed the fare; nothing for 1.0. */
function multiplier(value: number | null): string | null {
  if (value == null || Number(value) === 1) return null;
  return `×${Number(value).toFixed(2).replace(/\.?0+$/, '')}`;
}

/**
 * "Fare details" on her receipt: how the one price she was shown when she
 * booked was reached - base fare, distance, time, night or surge, the
 * minimum-fare note - then the promotion and what she paid.
 * <p>
 * There is no insurance line, and there never will be: cover is SheOut's
 * cost, paid from its commission, not part of any fare. Taxes, once SheOut
 * is GST-registered, are shown as included in the same price, with the
 * invoice - see TaxInvoiceLines.
 */
export function FareDetailsCard({ bookingId, children }: { bookingId: string; children?: React.ReactNode }) {
  const { t } = useTranslation();
  const [details, setDetails] = useState<FareDetails | null>(null);

  useEffect(() => {
    getFareDetails(bookingId).then(setDetails).catch(() => setDetails(null));
  }, [bookingId]);

  if (!details) return null;
  const night = multiplier(details.nightMultiplier);
  const surge = multiplier(details.surgeMultiplier);
  const row = (label: string, amount: number | null, testid?: string) =>
    amount == null ? null : (
      <div className="flex items-center justify-between" data-testid={testid}>
        <span className="text-text-secondary">{label}</span>
        <AmountText size="sm" exact amount={Number(amount)} />
      </div>
    );

  return (
    <Card className="space-y-2 text-sm" data-testid="fare-details">
      <p className="font-heading text-card-title text-text-primary">{t('fareDetails.title')}</p>
      {details.breakdownRecorded ? (
        <>
          {row(t('fareDetails.base'), details.baseFare)}
          {row(t('fareDetails.distance'), details.distanceCharge)}
          {row(t('fareDetails.time'), details.timeCharge)}
          {night && <p className="text-xs text-text-secondary">{t('fareDetails.night', { multiplier: night })}</p>}
          {surge && <p className="text-xs text-text-secondary">{t('fareDetails.surge', { multiplier: surge })}</p>}
          {details.minimumFareApplied && <p className="text-xs text-text-secondary">{t('fareDetails.minimum')}</p>}
        </>
      ) : (
        <p className="text-xs text-text-secondary">
          {details.repricedByDestinationChange ? t('fareDetails.repriced') : t('fareDetails.noBreakdown')}
        </p>
      )}
      <div className="border-t border-border pt-2">
        {row(t('fareDetails.fare'), details.fare, 'fare-total')}
        {Number(details.promoDiscount) > 0 && (
          <div className="flex items-center justify-between">
            <span className="text-text-secondary">{details.promotionName ?? t('fareDetails.promo')}</span>
            <AmountText size="sm" exact sign="positive" amount={Number(details.promoDiscount)} />
          </div>
        )}
        <div className="flex items-center justify-between font-semibold">
          <span className="text-text-primary">{t('fareDetails.paid')}</span>
          <AmountText size="sm" exact amount={Number(details.amountDue)} />
        </div>
      </div>
      {children}
    </Card>
  );
}
