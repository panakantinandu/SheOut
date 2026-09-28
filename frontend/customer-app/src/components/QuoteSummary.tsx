import { Clock, Flag } from 'lucide-react';
import { AmountText, ServiceArt, serviceArtFor, useTranslation, type ServiceArtKind } from '@sheout/design-system';
import type { GeoAddress } from '../api/types';
import type { FareQuoteState } from '../lib/useFareQuote';

/**
 * The top of the booking sheet: what she is booking, what it costs, and
 * when she would be picked up and dropped.
 * <p>
 * The price is exact, labelled as fixed: SheOut charges the fare quoted when
 * she books, whatever the traffic, so a range would suggest a variation that
 * never happens. The two times are estimates and read as such - "Arriving in
 * 5 min" is the nearest available partner's road time to the pickup, and
 * "Drop by" adds that wait to the trip's own duration. With nobody nearby
 * there is no time to give, and the sheet says so rather than guessing.
 */
export function QuoteSummary({
  state,
  kind,
  serviceName,
  pickup,
  drop,
}: {
  state: FareQuoteState;
  kind: ServiceArtKind;
  serviceName: string;
  pickup: GeoAddress | null;
  drop: GeoAddress | null;
}) {
  const { t, i18n } = useTranslation();
  const { quote, loading, error } = state;
  const promo = quote && quote.promoDiscount > 0 ? quote : null;
  const dropTime = quote?.dropBy
    ? new Date(quote.dropBy).toLocaleTimeString(i18n.language, { hour: 'numeric', minute: '2-digit' })
    : null;

  let times: string;
  if (!pickup || !drop) times = t('booking.chooseDropForFare');
  else if (error) times = t('fare.errorNote', { error });
  else if (!quote) times = t('booking.checkingQuote');
  else if (quote.pickupEtaMinutes != null && dropTime) times = '';
  else times = t('booking.noPartnerNow');

  return (
    <div className="flex items-start gap-3" data-testid="quote-summary">
      <ServiceArt kind={quote ? serviceArtFor(quote.category) : kind} size="md" />
      <div className="min-w-0 flex-1">
        <p className="font-heading text-card-title text-text-primary">{serviceName}</p>
        {quote && quote.pickupEtaMinutes != null && dropTime ? (
          <div className="mt-0.5 space-y-0.5 text-sm" data-testid="quote-times">
            <p className="flex items-center gap-1.5 font-semibold text-accent-green-strong">
              <Clock className="h-3.5 w-3.5" aria-hidden="true" />
              {t('booking.arrivingIn', { count: quote.pickupEtaMinutes })}
            </p>
            <p className="flex items-center gap-1.5 text-text-secondary">
              <Flag className="h-3.5 w-3.5" aria-hidden="true" />
              {t('booking.dropBy', { time: dropTime })}
            </p>
          </div>
        ) : (
          <p className={`mt-0.5 text-sm ${error ? 'text-danger' : 'text-text-secondary'}`} data-testid="quote-times-note">
            {loading && !quote && pickup && drop ? t('booking.checkingQuote') : times}
          </p>
        )}
      </div>
      {quote && (
        <div className="shrink-0 text-right" data-testid="quote-price">
          {promo && (
            <p className="text-xs text-text-secondary line-through">₹{Math.round(promo.fareEstimate)}</p>
          )}
          <AmountText amount={promo ? promo.youPay : quote.fareEstimate} size="lg" />
          <p className="text-xs text-text-secondary">{t('booking.fixedPrice')}</p>
        </div>
      )}
    </div>
  );
}
