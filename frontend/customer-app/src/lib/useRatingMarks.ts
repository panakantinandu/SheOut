import { useEffect, useState } from 'react';
import { ratingsApi } from '../api/client';
import type { BookingSummary, Rating } from '../api/types';

/**
 * Whether each trip on a history page has already been rated by the person
 * looking at it.
 * <p>
 * Exists so a list can say "rated" or offer a "Rate trip" action without
 * prompting somebody again for a trip they already rated somewhere else -
 * which is the fastest way to make a rating prompt feel like nagging and to
 * get it dismissed reflexively from then on.
 * <p>
 * One request for the whole page rather than one per row. A history list is
 * exactly where an N+1 stops being theoretical.
 */
export function useRatingMarks(bookings: BookingSummary[]): Map<string, Rating> {
  const [marks, setMarks] = useState<Map<string, Rating>>(new Map());

  // Only completed trips can be rated, so nothing else is worth asking
  // about. The key is the joined id list, so this refetches when the page
  // changes and not on every render that happens to produce a new array.
  const completedIds = bookings
    .filter((booking) => booking.status === 'COMPLETED')
    .map((booking) => booking.id);
  const key = completedIds.join(',');

  useEffect(() => {
    if (completedIds.length === 0) {
      setMarks(new Map());
      return;
    }
    let cancelled = false;
    ratingsApi
      .forBookings(completedIds)
      .then((ratings) => {
        if (cancelled) return;
        setMarks(new Map(ratings.map((rating) => [rating.bookingId, rating])));
      })
      .catch(() => {
        // The list still renders, just without the marks. Nothing here is
        // worth an error banner over a history page.
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  return marks;
}
