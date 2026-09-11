import { useEffect, useRef, useState } from 'react';
import { dispatchApi } from '../api/client';

// A real GPS subscription (watchPosition) rather than repeated one-off
// getCurrentPosition calls - it fires on every OS-reported movement, not on
// a timer. The interval below controls only how often the latest watched
// position is actually SENT, so GPS jitter doesn't spam dispatch.
export const LOCATION_SEND_MS = 7000;

export type LocationStatus = 'locating' | 'sharing' | 'blocked';

export interface LocationBroadcast {
  position: { lat: number; lng: number } | null;
  status: LocationStatus;
  /** Why sharing failed, in words a driver can act on. Null unless blocked. */
  error: string | null;
}

function describe(err: GeolocationPositionError): string {
  if (err.code === err.PERMISSION_DENIED) {
    return 'Location permission is blocked. Allow it in your browser settings to receive ride requests.';
  }
  if (err.code === err.POSITION_UNAVAILABLE) {
    return 'Your location is unavailable right now, so you will not receive ride requests.';
  }
  return 'Finding your location is taking too long, so you will not receive ride requests.';
}

/**
 * Broadcasts this driver's position to dispatch while `enabled`, and
 * reports whether it is actually managing to.
 * <p>
 * This used to fall back to a fixed central-Hyderabad coordinate whenever
 * the device would not give a position, and broadcast that as though it
 * were real. Nothing told the driver, so a driver with location blocked
 * appeared to dispatch to be sitting in the middle of the city: customers
 * were matched to them, and the customer's tracking map drew them at a
 * place they had never been. A fabricated position is worse than no
 * position, and this is a safety app.
 * <p>
 * So nothing is sent until the device gives a real fix. If it never does,
 * the driver is simply not in the geo index - which is the truth - and the
 * screens using this hook say so plainly rather than leaving them to wonder
 * why no requests arrive.
 * <p>
 * It is also a shared hook rather than an effect on one screen because the
 * driver must keep broadcasting across a navigation: it used to live only
 * in Home, which unmounts when a trip opens, so broadcasting stopped at
 * exactly the moment the customer's tracking map needed it.
 */
export function useLocationBroadcast(enabled: boolean): LocationBroadcast {
  const [position, setPosition] = useState<{ lat: number; lng: number } | null>(null);
  const [status, setStatus] = useState<LocationStatus>('locating');
  const [error, setError] = useState<string | null>(null);
  // The sender reads a ref, not state - it must send the newest fix on each
  // tick without the interval being torn down and rebuilt on every update.
  const latest = useRef<{ lat: number; lng: number } | null>(null);

  useEffect(() => {
    if (!enabled) {
      setStatus('locating');
      setError(null);
      latest.current = null;
      return;
    }

    if (!navigator.geolocation) {
      setStatus('blocked');
      setError('This browser cannot share your location, so you will not receive ride requests.');
      return;
    }

    let watchId: number | null = navigator.geolocation.watchPosition(
      (pos) => {
        latest.current = { lat: pos.coords.latitude, lng: pos.coords.longitude };
        setPosition(latest.current);
        setStatus('sharing');
        setError(null);
      },
      (err) => {
        // Only a failure with nothing already known is fatal. Once a real
        // fix exists, a transient watch error should not stop broadcasting
        // the last genuine position.
        if (!latest.current) {
          setStatus('blocked');
          setError(describe(err));
        }
      },
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 8000 }
    );

    const send = () => {
      // Never invent a position. No real fix means no broadcast.
      if (!latest.current) return;
      dispatchApi.recordLocation(latest.current.lat, latest.current.lng).catch(() => {});
    };
    send();
    const interval = setInterval(send, LOCATION_SEND_MS);

    return () => {
      if (watchId !== null) navigator.geolocation.clearWatch(watchId);
      watchId = null;
      clearInterval(interval);
    };
  }, [enabled]);

  return { position, status, error };
}
