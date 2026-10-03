import { useEffect, useState } from 'react';
import { payoutsApi } from '../api/client';

/**
 * Her share of each paid trip, by booking id - what her wallet was credited
 * with, the fare less SheOut's commission. What Earnings and Home add up:
 * they used to add up fares, so a ₹100 trip read as ₹100 earned when she
 * was paid ₹82.
 * <p>
 * Fetched again only when refreshKey changes (the number of paid trips),
 * not on every poll of the trips list.
 */
export function useTripShares(refreshKey: unknown): Map<string, number> | null {
  const [shares, setShares] = useState<Map<string, number> | null>(null);
  useEffect(() => {
    payoutsApi
      .earnings()
      .then((list) => setShares(new Map(list.map((e) => [e.bookingId, Number(e.share)]))))
      .catch(() => setShares(null));
  }, [refreshKey]);
  return shares;
}

/** Her share of a trip; zero until the credit is there, never the fare. */
export function shareOf(shares: Map<string, number> | null, bookingId: string): number {
  return shares?.get(bookingId) ?? 0;
}
