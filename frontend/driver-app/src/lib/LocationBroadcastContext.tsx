import { i18next } from '@sheout/design-system';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { dispatchApi } from '../api/client';

// A real GPS subscription (watchPosition) rather than repeated one-off
// getCurrentPosition calls - it fires on every OS-reported movement, not on
// a timer. The interval below controls only how often the latest watched
// position is actually SENT, so GPS jitter doesn't spam dispatch.
export const LOCATION_SEND_MS = 7000;

/** A fix less accurate than this is a network fallback, not GPS. */
const POOR_FIX_METRES = 50;
/** How long a good fix outranks a poor one. */
const TRUST_GOOD_FIX_MS = 15000;

/**
 * A position report older than this, and dispatch may no longer be matching
 * her: three missed sends. The Home status stops saying "looking for
 * requests" and says it is reconnecting instead.
 */
export const LOCATION_STALE_MS = 3 * LOCATION_SEND_MS;

/** What the browser says about location access; 'unknown' where the Permissions API is missing (older Safari). */
export type LocationPermission = 'granted' | 'denied' | 'prompt' | 'unknown';

export type LocationStatus = 'idle' | 'locating' | 'sharing' | 'blocked';

export interface LocationBroadcast {
  /**
   * heading: the device's direction of travel, degrees from north, when it
   * reports one - only while actually moving; null when stopped or unknown.
   * It turns her own bike marker and is never sent anywhere. accuracy is
   * the device's own radius of uncertainty in metres, for in-app navigation
   * to tell a real wrong turn from GPS wobble; also never sent.
   */
  position: { lat: number; lng: number; heading?: number | null; accuracy?: number | null } | null;
  status: LocationStatus;
  /** Why sharing failed, in words a driver can act on. Null unless blocked. */
  error: string | null;
  /** Location access, live: it changes the moment she allows or blocks it in settings. */
  permission: LocationPermission;
  /** When the server last accepted a position report from this phone; null before the first. */
  lastReportAt: number | null;
  /** When sharing last started, to allow the first report a moment to land. Null while idle. */
  activeSince: number | null;
}

interface LocationBroadcastContextValue extends LocationBroadcast {
  retain: () => () => void;
  restart: () => void;
}

export interface SharedLocation extends LocationBroadcast {
  /** Start the watch again - after access has been granted where nothing else would say so. */
  restart: () => void;
}

const Ctx = createContext<LocationBroadcastContextValue | null>(null);

function describe(err: GeolocationPositionError): string {
  if (err.code === err.PERMISSION_DENIED) {
    return i18next.t('location.blocked');
  }
  if (err.code === err.POSITION_UNAVAILABLE) {
    return i18next.t('location.unavailable');
  }
  return i18next.t('location.timeout');
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
  const [position, setPosition] = useState<LocationBroadcast['position']>(null);
  const [status, setStatus] = useState<LocationStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const [holders, setHolders] = useState(0);
  const [permission, setPermission] = useState<LocationPermission>('unknown');
  const [lastReportAt, setLastReportAt] = useState<number | null>(null);
  const [activeSince, setActiveSince] = useState<number | null>(null);

  // Location access, watched live. Allowing location in the phone's settings
  // and coming back must bring her back online without a reload; blocking it
  // must show at once, not after the next failed fix.
  useEffect(() => {
    const perms = typeof navigator !== 'undefined' ? navigator.permissions : undefined;
    if (!perms?.query) return;
    let status: PermissionStatus | null = null;
    let cancelled = false;
    const update = () => status && setPermission(status.state as LocationPermission);
    perms
      .query({ name: 'geolocation' as PermissionName })
      .then((s) => {
        if (cancelled) return;
        status = s;
        update();
        s.addEventListener('change', update);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
      status?.removeEventListener('change', update);
    };
  }, []);
  // A new watch when access is granted again: a watch that already failed
  // for want of permission does not start delivering by itself.
  const permissionEpoch = permission;
  // Where the Permissions API is missing (older Safari) nothing announces a
  // grant, so a screen that has just read a position asks for a new watch.
  const [restartEpoch, setRestartEpoch] = useState(0);
  const restart = useCallback(() => setRestartEpoch((n) => n + 1), []);
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
      setActiveSince(null);
      setLastReportAt(null);
      return;
    }
    setActiveSince(Date.now());

    if (!navigator.geolocation) {
      setStatus('blocked');
      setError(i18next.t('location.unsupported'));
      return;
    }

    setStatus('locating');
    const send = () => {
      // Never invent a position. No real fix means no broadcast. This app
      // used to fall back to a fixed central-Hyderabad coordinate and send
      // that as though it were real, which put partners on a rider's map at
      // a place they had never been.
      if (!latest.current) return;
      dispatchApi
        .recordLocation(latest.current.lat, latest.current.lng)
        // Only an accepted report counts: this is what "dispatch can see
        // her" means, and what the Home status is honest about.
        .then(() => setLastReportAt(Date.now()))
        .catch(() => {});
    };
    // The first fix is sent the moment it arrives rather than at the next
    // 7-second tick, so going online does not leave dispatch (and her Home
    // status) waiting on a timer. After that, the interval as before.
    let sentFirst = false;
    // The last fix good enough to trust, to weigh a poor one against.
    let lastGood: { at: number; accuracy: number } | null = null;
    let watchId: number | null = navigator.geolocation.watchPosition(
      (pos) => {
        // A phone briefly falling back to Wi-Fi or cell towers reports a fix
        // 100-300 m out. Taken as it comes, her marker leaps across a block
        // on both apps and jumps back with the next GPS fix. While a good fix
        // is recent, a poor one is dropped; with nothing better, it is used.
        const accuracy = Number.isFinite(pos.coords.accuracy) ? pos.coords.accuracy : null;
        const now = Date.now();
        if (accuracy != null && accuracy > POOR_FIX_METRES && lastGood && now - lastGood.at < TRUST_GOOD_FIX_MS && accuracy > lastGood.accuracy * 2) return;
        if (accuracy == null || accuracy <= POOR_FIX_METRES) lastGood = { at: now, accuracy: accuracy ?? POOR_FIX_METRES };
        latest.current = { lat: pos.coords.latitude, lng: pos.coords.longitude };
        if (!sentFirst) {
          sentFirst = true;
          send();
        }
        // A heading from a device standing still is noise, so it only counts
        // above walking pace.
        const { heading, speed } = pos.coords;
        const moving = heading != null && Number.isFinite(heading) && (speed == null || speed > 1);
        setPosition({ ...latest.current, heading: moving ? heading : null, accuracy });
        setStatus('sharing');
        setError(null);
        // A fix is proof of access, whatever the Permissions API last said -
        // some browsers never announce a grant made in the phone's settings.
        setPermission((p) => (p === 'denied' ? 'granted' : p));
      },
      (err) => {
        // Access taken away is fatal whenever it happens: she is no longer
        // sharing, and the old position must not go on being sent as if she
        // were still there. Anything else only matters with nothing already
        // known; once a real fix exists, a transient error should not stop
        // broadcasting the last genuine position.
        if (err.code === err.PERMISSION_DENIED) {
          latest.current = null;
          setPosition(null);
          setStatus('blocked');
          setError(describe(err));
        } else if (!latest.current) {
          setStatus('blocked');
          setError(describe(err));
        }
      },
      // Fresh fixes: in-app navigation turns on these, and a ten-second-old
      // position is a turn missed.
      { enableHighAccuracy: true, maximumAge: 2000, timeout: 8000 }
    );

    send();
    const interval = setInterval(send, LOCATION_SEND_MS);

    return () => {
      if (watchId !== null) navigator.geolocation.clearWatch(watchId);
      watchId = null;
      clearInterval(interval);
    };
  }, [active, permissionEpoch, restartEpoch]);

  const value = useMemo(
    () => ({ position, status, error, permission, lastReportAt, activeSince, retain, restart }),
    [position, status, error, permission, lastReportAt, activeSince, retain, restart]
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

/**
 * One position, now, without starting a broadcast.
 * <p>
 * Going online needs a fix BEFORE the request is sent - the server has to
 * decide whether she is anywhere SheOut operates - but she is not sharing
 * her location yet, and should not be until she has actually gone online.
 * A continuous watch just to answer one question would be sharing her
 * whereabouts while she is still deciding whether to work.
 * <p>
 * So: a single read, triggered by her own tap, and nothing retained. Falls
 * back to the last watched fix when one already exists, which is the case
 * when she is already online and toggling off and on again.
 */
export function readPositionOnce(fallback: { lat: number; lng: number } | null):
    Promise<{ lat: number; lng: number } | null> {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve(fallback);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      // Never invent one. A null here becomes "we need your location",
      // which is the truth; a guessed coordinate would put her in a
      // dispatch queue for a place she is not.
      () => resolve(fallback),
      { enableHighAccuracy: true, maximumAge: 30000, timeout: 10000 }
    );
  });
}

/**
 * Shares this partner's position for as long as {@code active} is true and
 * this component is mounted, and hands back the latest fix.
 * <p>
 * Callers say WHY they need it by what they pass - "I am online", "this
 * booking is live" - and never how. Whether a watch is running, and whether
 * another screen also wants one, is the provider's business.
 */
export function useShareLocation(active: boolean): SharedLocation {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useShareLocation must be used inside LocationBroadcastProvider');
  const { retain, restart, position, status, error, permission, lastReportAt, activeSince } = ctx;

  useEffect(() => {
    if (!active) return;
    return retain();
  }, [active, retain]);

  return { position, status, error, permission, lastReportAt, activeSince, restart };
}
