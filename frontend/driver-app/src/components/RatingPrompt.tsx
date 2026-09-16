import { useCallback, useEffect, useState } from 'react';
import { RatingDialog } from '@sheout/design-system';
import { ApiError, ratingsApi } from '../api/client';
import type { Rating, RatingTagCatalogue } from '../api/types';

export interface RatingPromptProps {
  /**
   * Ask about this trip specifically. Omit to ask about whatever is waiting
   * and closing soonest, which is what a screen with no trip of its own
   * (Home, the tab bar) should do.
   */
  bookingId?: string;
  counterpartLabel?: string;
  /** Called once a rating actually lands, so a caller can refresh what it shows. */
  onRated?: (rating: Rating) => void;
}

/**
 * Asks the partner to rate a finished trip, once.
 * <p>
 * Which trip to ask about comes from the server, never from this screen's
 * own idea of what has finished: the open slot and its deadline live in the
 * ratings module, so a trip that has already been rated, or whose window has
 * closed, never produces a prompt here. That is what stops somebody being
 * asked twice for the same ride from two different screens.
 * <p>
 * Dismissing is remembered for the session only. Rating is optional and
 * nagging is the fastest way to make people tap a star at random to make it
 * stop - but a partner who skips it between trips should still find an easy
 * way to rate later from her bookings, because that is when she has a
 * moment.
 */
export function RatingPrompt({ bookingId, counterpartLabel = 'your rider', onRated }: RatingPromptProps) {
  const [slot, setSlot] = useState<Rating | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tagOptions, setTagOptions] = useState<RatingTagCatalogue | null>(null);

  // Fetched once, and its failure is ignored on purpose: no tags is a
  // smaller loss than a prompt that will not open.
  useEffect(() => {
    ratingsApi.tags().then(setTagOptions).catch(() => {});
  }, []);

  const load = useCallback(async () => {
    try {
      if (bookingId) {
        const found = await ratingsApi.forBooking(bookingId);
        setSlot(found.stars === null ? found : null);
        return;
      }
      const pending = await ratingsApi.pending();
      setSlot(pending.length > 0 ? pending[0] : null);
    } catch {
      // A 404 here means there is simply nothing to rate - a trip that never
      // completed, or one already rated. Not worth showing anybody.
      setSlot(null);
    }
  }, [bookingId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function submit(stars: number, comment?: string, tags?: string[]) {
    if (!slot) return;
    setBusy(true);
    setError(null);
    try {
      const rating = await ratingsApi.submit(slot.bookingId, stars, comment, tags);
      setSlot(null);
      onRated?.(rating);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send that rating');
      if (err instanceof ApiError && err.status === 409) {
        // Already rated elsewhere, or the window closed while this was open.
        // Either way there is nothing left to ask.
        setSlot(null);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <RatingDialog
      open={slot !== null && !dismissed}
      counterpartLabel={counterpartLabel}
      busy={busy}
      error={error}
      tagOptions={tagOptions}
      onSubmit={submit}
      onSkip={() => setDismissed(true)}
    />
  );
}
