import { useEffect, useState } from 'react';
import type { MapMarker } from '@sheout/design-system';
import { dispatchApi } from '../api/client';
import type { BookingCategory } from '../api/types';

const POLL_MS = 10_000;

/**
 * "Partners near you" markers for a booking screen's map, before anything is
 * booked - the same reassurance every ride app gives on this screen.
 * <p>
 * Polled every ten seconds while the screen is open and the tab is visible;
 * nothing is fetched in a background tab. The positions are the server's
 * blurred ones (see the backend's NearbyDriverPreview), shown as they are.
 * <p>
 * A failed poll keeps the last answer rather than clearing the map: an empty
 * map reads as "nobody is around", which a dropped request does not mean.
 * Nothing is shown until there is a pickup to look around.
 */
export function useNearbyDrivers(
  pickup: { lat: number; lng: number } | null | undefined,
  category: BookingCategory,
  label: string
): MapMarker[] {
  const [points, setPoints] = useState<{ lat: number; lng: number }[]>([]);
  const lat = pickup?.lat;
  const lng = pickup?.lng;

  useEffect(() => {
    if (lat == null || lng == null) {
      setPoints([]);
      return;
    }
    let cancelled = false;
    const load = () => {
      if (document.visibilityState === 'hidden') return;
      dispatchApi
        .nearbyDrivers(lat, lng, category)
        .then((r) => {
          if (!cancelled) setPoints(r.drivers);
        })
        .catch(() => undefined);
    };
    load();
    const timer = window.setInterval(load, POLL_MS);
    document.addEventListener('visibilitychange', load);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', load);
    };
  }, [lat, lng, category]);

  return points.map((p, i) => ({ key: `nearby-${i}`, lat: p.lat, lng: p.lng, label, kind: 'nearby' as const }));
}
