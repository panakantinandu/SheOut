import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from '@sheout/design-system';
import type { GeoAddress } from '../api/types';
import { distanceKm, reverseGeocode } from './geocode';

/** Closer than this to where the pin started is "she did not move it". */
const SAME_SPOT_KM = 0.008;

export interface CenterPinAddress {
  /** The address under the pin, once known. */
  address: GeoAddress | null;
  /** The map is moving under the pin right now. */
  moving: boolean;
  /** The map has settled and the address is being looked up. */
  resolving: boolean;
  error: string | null;
  onMoveStart: () => void;
  onIdle: (lat: number, lng: number) => void;
  retry: () => void;
}

/**
 * The address under a centre pin, kept in step as she moves the map.
 * <p>
 * Looked up when the map settles, never while it moves: a lookup per frame
 * would be hundreds of requests to a geocoder that asks for one a second,
 * and the address flickering under her finger is harder to read than one
 * that updates when she stops. A lookup still running when she moves again
 * is abandoned, so a slow answer for where the pin was cannot replace the
 * answer for where it is.
 * <p>
 * Left where it started, the pin keeps the place's own name - "Hitech City"
 * from her search, not whatever the reverse lookup calls that corner.
 */
export function useCenterPinAddress(start: GeoAddress | null, active: boolean): CenterPinAddress {
  const { t } = useTranslation();
  const [address, setAddress] = useState<GeoAddress | null>(start);
  const [moving, setMoving] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const startRef = useRef<GeoAddress | null>(start);
  const lastPoint = useRef<{ lat: number; lng: number } | null>(null);
  const lookup = useRef<AbortController | null>(null);

  useEffect(() => {
    lookup.current?.abort();
    if (!active) return;
    startRef.current = start;
    lastPoint.current = start ? { lat: start.lat, lng: start.lng } : null;
    setAddress(start);
    setMoving(false);
    setResolving(false);
    setError(null);
    // Only on opening: a new `start` object on a re-render is not a new pin.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active]);

  useEffect(() => () => lookup.current?.abort(), []);

  const resolve = useCallback(
    async (lat: number, lng: number) => {
      lookup.current?.abort();
      const controller = new AbortController();
      lookup.current = controller;
      lastPoint.current = { lat, lng };
      setResolving(true);
      setError(null);
      try {
        const found = await reverseGeocode(lat, lng, controller.signal);
        if (!controller.signal.aborted) setAddress(found);
      } catch (err) {
        if ((err as Error).name === 'AbortError' || controller.signal.aborted) return;
        setAddress(null);
        setError((err as Error).message === 'No address found at that point' ? t('picker.noAddress') : t('picker.lookupError'));
      } finally {
        if (!controller.signal.aborted) setResolving(false);
      }
    },
    [t]
  );

  const onMoveStart = useCallback(() => {
    setMoving(true);
  }, []);

  const onIdle = useCallback(
    (lat: number, lng: number) => {
      setMoving(false);
      const here = { lat, lng };
      const origin = startRef.current;
      if (origin && distanceKm(origin, here) < SAME_SPOT_KM) {
        lookup.current?.abort();
        lastPoint.current = { lat: origin.lat, lng: origin.lng };
        setAddress(origin);
        setResolving(false);
        setError(null);
        return;
      }
      // Settled where it already was (a zoom without a pan): nothing new to look up.
      if (lastPoint.current && distanceKm(lastPoint.current, here) < SAME_SPOT_KM) return;
      void resolve(lat, lng);
    },
    [resolve]
  );

  const retry = useCallback(() => {
    if (lastPoint.current) void resolve(lastPoint.current.lat, lastPoint.current.lng);
  }, [resolve]);

  return { address, moving, resolving, error, onMoveStart, onIdle, retry };
}
