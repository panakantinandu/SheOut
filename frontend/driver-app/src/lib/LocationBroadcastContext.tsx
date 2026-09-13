import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { dispatchApi } from '../api/client';

// A real GPS subscription (watchPosition) rather than repeated one-off
// getCurrentPosition calls - it fires on every OS-reported movement, not on
// a timer. The interval below controls only how often the latest watched
// position is actually SENT, so GPS jitter doesn't spam dispatch.
export const LOCATION_SEND_MS = 7000;

export type LocationStatus = 'idle' | 'locating' | 'sharing' | 'blocked';

export interface LocationBroadcast {
  position: { lat: number; lng: number } | null;
  status: LocationStatus;
  /** Why sharing failed, in words a driver can act on. Null unless blocked. */
  error: string | null;
}

interface LocationBroadcastContextValue extends LocationBroadcast {
  retain: () => () => void;
}

const Ctx = createContext<LocationBroadcastContextValue | null>(null);

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
 * The single place this app shares a partner's position from.
 * <p>
 * WHY A PROVIDER AND NOT A HOOK PER SCREEN. It used to be a hook, and each
 * screen decided for itself whether to broadcast. The conditions drifted,
 * as conditions in three places do: Home broadcast while online, Trip
 * broadcast during ACCEPTED and IN_PROGRESS, and the Offer screen - which
 * is the screen a partner is actually looking at during MATCHED - did not
 * broadcast at all. So from the moment a partner was offered a booking to
 * the moment she accepted it, her app sent nothing, and the rider watching
 * her approach saw a marker frozen at the last position before the offer
 * arrived. The position was real, which is why it never looked broken; it
 * was just several minutes old.
 * <p>
 * Hoisting it fixes a second thing that was never noticed. The hook lived
 * inside each screen, so navigating Home -> Offer -> Trip tore the GPS
 * subscription down and built it up twice, and watchPosition takes seconds
 * to produce its first fix. Even on the paths that did broadcast, there was
 * a hole at every navigation. One subscription above the router has no
 * seams.
 * <p>
 * WHY IT IS STILL GATED. Broadcasting whenever the app is open would be
 * simpler and is not acceptable: a partner who has gone offline is off the
 * clock, and continuing to send her position would be tracking her, not
 * dispatching her. Screens retain it while there is a real reason to share
 * - she is online, or she has a live booking - and the last release stops
 * the watch.
 */
export function LocationBroadcastProvider({ children }: { children: ReactNode }) {
  const [position, setPosition] = useState<{ lat: number; lng: number } | null>(null);
  const [status, setStatus] = useState<LocationStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const [holders, setHolders] = useState(0);
  // The sender reads a ref, not state - it must send the newest fix on each
  // tick without the interval being torn down and rebuilt on every update.
  const latest = useRef<{ lat: number; lng: number } | null>(null);

  const retain = useCallback(() => {
    setHolders((n) => n + 1);
    let released = false;
    return () => {
      // Guarded because React 18's StrictMode runs effect cleanups twice in
      // development; without this the count would go negative and the watch
      // would stop while a screen still needed it.
      if (released) return;
      released = true;
      setHolders((n) => Math.max(0, n - 1));
    };
  }, []);

  const active = holders > 0;

  useEffect(() => {
    if (!active) {
      setStatus('idle');
      setError(null);
      latest.current = null;
      setPosition(null);
      return;
    }

    if (!navigator.geolocation) {
      setStatus('blocked');
      setError('This browser cannot share your location, so you will not receive ride requests.');
      return;
    }

    setStatus('locating');
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
      // Never invent a position. No real fix means no broadcast. This app
      // used to fall back to a fixed central-Hyderabad coordinate and send
      // that as though it were real, which put partners on a rider's map at
      // a place they had never been.
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
  }, [active]);

  const value = useMemo(
    () => ({ position, status, error, retain }),
    [position, status, error, retain]
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

/**
 * Shares this partner's position for as long as {@code active} is true and
 * this component is mounted, and hands back the latest fix.
 * <p>
 * Callers say WHY they need it by what they pass - "I am online", "this
 * booking is live" - and never how. Whether a watch is running, and whether
 * another screen also wants one, is the provider's business.
 */
export function useShareLocation(active: boolean): LocationBroadcast {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useShareLocation must be used inside LocationBroadcastProvider');
  const { retain, position, status, error } = ctx;

  useEffect(() => {
    if (!active) return;
    return retain();
  }, [active, retain]);

  return { position, status, error };
}
