import { Bike } from 'lucide-react';
import { AmountText, Card, IconCircle } from '@sheout/design-system';
import type { FareQuoteState } from '../lib/useFareQuote';

/**
 * The mockup's "Estimated Fare ₹42 - 58 (2.8 km)" card, shown before the
 * customer commits to a booking.
 * <p>
 * FLAGGED DEVIATION: a single amount, not a range. The mockup shows a
 * spread, but this pricing is deterministic - the quote endpoint returns
 * one number, and it is exactly the number the booking is then created
 * with. Printing "₹42 - 58" around it would invent a variability the
 * pricing does not have, and would not match what the customer is
 * actually charged. If pricing ever gains surge or a real range, this card
 * renders whatever the quote returns.
 * <p>
 * The distance is straight-line, the distance the fare was derived from,
 * not the distance the trip will cover - hence "approx".
 */
export function FareEstimateCard({ state }: { state: FareQuoteState }) {
  const { quote, loading, error } = state;

  if (error) {
    return (
      <Card className="flex items-center gap-3">
        <IconCircle tone="soft" size="sm" icon={<Bike />} />
        <p className="text-sm text-text-secondary">{error}. You can still book - the fare is confirmed on booking.</p>
      </Card>
    );
  }

  if (!quote && !loading) return null;

  return (
    <Card className="flex items-center gap-3">
      <IconCircle tone="soft" icon={<Bike />} />
      <div className="flex-1">
        <p className="text-sm text-text-secondary">Estimated Fare</p>
        {loading && !quote ? (
          <p className="font-heading font-semibold text-text-secondary">Calculating...</p>
        ) : (
          <p className="text-xs text-text-secondary">approx. {quote!.distanceKm} km</p>
        )}
      </div>
      {quote && <AmountText amount={quote.fareEstimate} size="lg" />}
    </Card>
  );
}
