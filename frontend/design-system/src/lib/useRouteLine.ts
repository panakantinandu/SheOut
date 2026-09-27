import { useEffect, useState } from 'react';
import type { RoutePoint } from '../components/LiveMap';

export interface RouteLine {
  points: RoutePoint[];
  distanceKm: number | null;
  durationMinutes: number | null;
}

/** The app's own call to GET /api/v1/routes/preview - each app has its own API client. */
export type RouteLineFetcher = (from: RoutePoint, to: RoutePoint) => Promise<RouteLine>;

// One answer per pair of points for the life of the page: a road does not
// move, and a rider toggling between screens should not ask twice.
const cache = new Map<string, RoutePoint[]>();
const keyOf = (from: RoutePoint, to: RoutePoint) =>
  [from.lat, from.lng, to.lat, to.lng].map((v) => v.toFixed(5)).join(',');

/**
 * The road from `from` to `to`, for LiveMap's `route` - SheOut's own OSRM,
 * via the app's fetcher. Empty until both points exist and the answer is in;
 * empty for good if the router cannot be reached, because LiveMap never
 * draws a straight line in place of a road.
 * <p>
 * Waits a moment after the points change, so dragging a pin asks once
 * where it lands, not at every step.
 */
export function useRouteLine(fetcher: RouteLineFetcher, from: RoutePoint | null | undefined, to: RoutePoint | null | undefined): RoutePoint[] {
  const key = from && to ? keyOf(from, to) : null;
  const [points, setPoints] = useState<RoutePoint[]>(() => (key && cache.get(key)) || []);

  useEffect(() => {
    if (!key || !from || !to) {
      setPoints([]);
      return;
    }
    const cached = cache.get(key);
    if (cached) {
      setPoints(cached);
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(() => {
      fetcher(from, to)
        .then((line) => {
          cache.set(key, line.points);
          if (!cancelled) setPoints(line.points);
        })
        .catch(() => {
          if (!cancelled) setPoints([]);
        });
    }, 350);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
    // from/to are read through the key, which is what identifies the pair.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, fetcher]);

  return points;
}
