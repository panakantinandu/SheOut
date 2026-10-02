import { ChevronRight, Navigation } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle } from '@sheout/design-system';
import { bookingApi } from '../api/client';
import type { BookingSummary } from '../api/types';
import { useTranslation } from '@sheout/design-system';

/** Most important first: a trip she is on, then one coming for her, then a search. */
const ORDER = ['IN_PROGRESS', 'ACCEPTED', 'MATCHED', 'REQUESTED'] as const;
const RESUMED_KEY = 'sheout_live_trip_resumed';

function liveTripOf(bookings: BookingSummary[]): BookingSummary | null {
  for (const status of ORDER) {
    const found = bookings.find((b) => b.status === status);
    if (found) return found;
  }
  return null;
}

/**
 * Her trip, if one is under way - read from the server, never from the phone.
 * <p>
 * The app keeps nothing about a trip on the phone, so after it crashed, was
 * swiped away or the phone restarted she opened onto Home with no sign of the
 * partner on her way, and had to find the trip through Live Track. Now: on
 * the first Home of a fresh start, a trip with a partner (coming or riding)
 * opens straight away - the screen she was on - and in any case this card
 * stays on Home while the trip is live.
 */
export function LiveTripBanner() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [trip, setTrip] = useState<BookingSummary | null>(null);

  useEffect(() => {
    let cancelled = false;
    bookingApi
      .listRecent(20)
      .then((mine) => {
        if (cancelled) return;
        const live = liveTripOf(mine);
        setTrip(live);
        if (live && live.status !== 'REQUESTED' && !alreadyResumed()) {
          navigate(`/tracking/${live.id}`);
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [navigate]);

  if (!trip) return null;
  const phase = trip.status === 'IN_PROGRESS' ? 'riding' : trip.status === 'REQUESTED' ? 'searching' : 'coming';

  return (
    <Card tone="brand" className="flex items-center gap-3" onClick={() => navigate(`/tracking/${trip.id}`)} data-testid="live-trip-banner">
      <IconCircle tone="solid" color="primary" icon={<Navigation />} />
      <div className="min-w-0 flex-1">
        <p className="font-heading text-card-title text-text-primary">{t(`liveTrip.${phase}`)}</p>
        <p className="text-xs text-text-secondary">{t('liveTrip.open')}</p>
      </div>
      <ChevronRight className="h-5 w-5 shrink-0 text-text-secondary" />
    </Card>
  );
}

/** Once per launch: going back to Home from the trip must not bounce her into it again. */
function alreadyResumed(): boolean {
  try {
    if (sessionStorage.getItem(RESUMED_KEY)) return true;
    sessionStorage.setItem(RESUMED_KEY, '1');
    return false;
  } catch {
    return true;
  }
}
