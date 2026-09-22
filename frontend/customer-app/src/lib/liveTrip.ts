import type { BookingSummary } from '../api/types';
import { bookingApi } from '../api/client';

const LIVE = new Set(['REQUESTED', 'MATCHED', 'ACCEPTED', 'IN_PROGRESS']);

/**
 * Her trip of this type that is still live, if any - for the moment the
 * server refuses a second one (ACTIVE_BOOKING_EXISTS) and the screen should
 * take her to the trip she has, not leave her reading a refusal.
 */
export async function findLiveTrip(type: BookingSummary['type']): Promise<string | null> {
  try {
    const mine = await bookingApi.listMine();
    return mine.find((b) => b.type === type && LIVE.has(b.status))?.id ?? null;
  } catch {
    return null;
  }
}
