import { useEffect, useState } from 'react';
import { ApiError, bookingApi } from '../api/client';
import type { BookingCategory, BookingType, FareQuote, GeoAddress } from '../api/types';

/** Long enough to swallow a burst of taps, short enough that the card feels immediate. */
const DEBOUNCE_MS = 400;

export interface FareQuoteInput {
  type: BookingType;
  category: BookingCategory;
  pickup: GeoAddress | null;
  drop: GeoAddress | null;
}

export interface FareQuoteState {
  quote: FareQuote | null;
  loading: boolean;
  error: string | null;
}

/**
 * Prices the trip as the customer picks pickup and drop, so the fare is on
 * screen before they commit to anything.
 * <p>
 * Debounced, and every in-flight request is invalidated when the inputs
 * change again. Without that second part a slow earlier request can land
 * after a newer one and leave the card showing the price of a destination
 * the customer already moved away from - the debounce alone does not
 * prevent that, it only makes it rarer.
 * <p>
 * Quoting needs both endpoints, so nothing is requested until both exist.
 * A failed quote is surfaced but never blocks booking: the fare is
 * recalculated server-side on creation regardless, so a missing quote
 * costs the customer a preview, not the trip.
 */
export function useFareQuote({ type, category, pickup, drop }: FareQuoteInput): FareQuoteState {
  const [state, setState] = useState<FareQuoteState>({ quote: null, loading: false, error: null });

  // Primitive deps, not the objects: a parent re-render hands back new
  // object identities for the same coordinates, which would re-fire the
  // quote on every render.
  const pickupKey = pickup ? `${pickup.lat},${pickup.lng}` : '';
  const dropKey = drop ? `${drop.lat},${drop.lng}` : '';

  useEffect(() => {
    if (!pickup || !drop) {
      setState({ quote: null, loading: false, error: null });
      return;
    }

    let cancelled = false;
    setState((prev) => ({ ...prev, loading: true, error: null }));

    const timer = setTimeout(async () => {
      try {
        const quote = await bookingApi.quote({ type, category, pickup, drop });
        if (!cancelled) setState({ quote, loading: false, error: null });
      } catch (err) {
        if (!cancelled) {
          setState({
            quote: null,
            loading: false,
            error: err instanceof ApiError ? err.message : 'Could not estimate the fare',
          });
        }
      }
    }, DEBOUNCE_MS);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type, category, pickupKey, dropKey]);

  return state;
}
