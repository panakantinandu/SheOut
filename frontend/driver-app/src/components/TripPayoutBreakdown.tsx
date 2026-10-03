import { useEffect, useState } from 'react';
import { AmountText, useTranslation } from '@sheout/design-system';
import { paymentsApi } from '../api/client';
import type { PaymentSummary } from '../api/types';

/**
 * One trip's money, as the payment recorded it when the rider paid: the
 * whole fare, SheOut's commission at the rate in force then, and her share.
 * Read from the payment rather than worked out here, so it is the figure
 * her wallet was actually credited with.
 */
export function TripPayoutBreakdown({ bookingId }: { bookingId: string }) {
  const { t } = useTranslation();
  const [payment, setPayment] = useState<PaymentSummary | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    paymentsApi.getForBooking(bookingId).then(setPayment).catch(() => setFailed(true));
  }, [bookingId]);

  if (failed) return <p className="px-4 pb-4 text-xs text-text-secondary">{t('earnings.breakdownUnavailable')}</p>;
  if (!payment) return <p className="px-4 pb-4 text-xs text-text-secondary">{t('common.loading')}</p>;
  const fare = payment.fareAmount ?? payment.amount;
  const commission = payment.driverPayout != null ? Number(fare) - Number(payment.driverPayout) : null;

  return (
    <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 px-4 pb-4 text-xs" data-testid={`payout-breakdown-${bookingId}`}>
      <dt className="text-text-secondary">{t('earnings.fare')}</dt>
      <dd className="text-right"><AmountText size="sm" exact amount={Number(fare)} /></dd>
      <dt className="text-text-secondary">{t('earnings.commission', { percent: payment.commissionPercent ?? '-' })}</dt>
      <dd className="text-right">{commission != null ? <AmountText size="sm" exact sign="negative" amount={commission} /> : '-'}</dd>
      <dt className="font-medium text-text-primary">{t('earnings.yourShare')}</dt>
      <dd className="text-right font-medium">{payment.driverPayout != null ? <AmountText size="sm" exact sign="positive" amount={Number(payment.driverPayout)} /> : '-'}</dd>
    </dl>
  );
}
