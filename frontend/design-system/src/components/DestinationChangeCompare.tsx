import { ArrowDown, MapPin } from 'lucide-react';
import { AmountText } from './AmountText';
import { useTranslation } from 'react-i18next';

export interface DestinationChangeCompareProps {
  oldDrop: string;
  newDrop: string;
  oldFare: number;
  newFare: number;
  oldDistanceKm?: number | null;
  newDistanceKm?: number | null;
  /** 'partner' says what the change means for her fare; 'rider' what it means for hers. Same numbers. */
  audience?: 'rider' | 'partner';
  className?: string;
}

/**
 * A change of destination, before and after, side by side: where the trip is
 * going now and where it would go, the fare each way, and the road distance
 * each fare was priced on.
 * <p>
 * The same block on both screens - the rider's before she sends the request
 * and her partner's when she answers it - so the two of them are looking at
 * the same figures. The difference is stated in words as well as numbers,
 * and in neutral colours: a lower fare on a shorter trip is not a loss to be
 * shown in red, and a higher one is not a penalty.
 */
export function DestinationChangeCompare({
  oldDrop,
  newDrop,
  oldFare,
  newFare,
  oldDistanceKm,
  newDistanceKm,
  audience = 'rider',
  className,
}: DestinationChangeCompareProps) {
  const { t } = useTranslation('ds');
  const difference = Math.round((newFare - oldFare) * 100) / 100;
  const km = (value?: number | null) => (value == null ? null : t('destinationChange.km', { km: Number(value).toFixed(1) }));

  return (
    <div className={className} data-testid="destination-compare">
      <div className="space-y-1 rounded-input bg-background p-3">
        <div className="flex items-start gap-2">
          <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-text-secondary" />
          <div className="min-w-0 flex-1">
            <p className="text-micro uppercase tracking-wide text-text-secondary">{t('destinationChange.currentDrop')}</p>
            <p className="text-sm text-text-secondary line-through decoration-text-secondary/40" data-testid="destination-compare-old">{oldDrop}</p>
          </div>
        </div>
        <ArrowDown className="ml-0.5 h-3 w-3 text-text-secondary" aria-hidden />
        <div className="flex items-start gap-2">
          <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-accent-orange" />
          <div className="min-w-0 flex-1">
            <p className="text-micro uppercase tracking-wide text-text-secondary">{t('destinationChange.newDrop')}</p>
            <p className="text-sm font-medium text-text-primary" data-testid="destination-compare-new">{newDrop}</p>
          </div>
        </div>
      </div>

      <dl className="mt-3 grid grid-cols-[auto_1fr_auto_1fr] items-baseline gap-x-2 gap-y-1 text-sm">
        <dt className="text-text-secondary">{t('destinationChange.fare')}</dt>
        <dd className="text-right text-text-secondary"><AmountText amount={oldFare} exact size="sm" /></dd>
        <dd className="text-text-secondary" aria-hidden>→</dd>
        <dd data-testid="destination-compare-new-fare"><AmountText amount={newFare} exact size="lg" /></dd>
        {(oldDistanceKm != null || newDistanceKm != null) && (
          <>
            <dt className="text-text-secondary">{t('destinationChange.distance')}</dt>
            <dd className="text-right text-text-secondary">{km(oldDistanceKm) ?? '-'}</dd>
            <dd className="text-text-secondary" aria-hidden>→</dd>
            <dd className="text-text-primary">{km(newDistanceKm) ?? '-'}</dd>
          </>
        )}
      </dl>

      <p className="mt-2 text-sm text-text-primary" data-testid="destination-compare-difference">
        {difference === 0
          ? t('destinationChange.sameFare')
          : t(
              `destinationChange.${audience}${difference > 0 ? 'More' : 'Less'}`,
              { amount: `₹${Math.abs(difference).toFixed(2)}` },
            )}
      </p>
      <p className="mt-1 text-caption text-text-secondary">{t('destinationChange.pricedFrom')}</p>
    </div>
  );
}
