import { useEffect, useRef, useState } from 'react';
import { dispatchApi } from '../api/client';

// A real GPS subscription (watchPosition) rather than repeated one-off
// getCurrentPosition calls - it fires on every OS-reported movement, not on
// a timer. The interval below controls only how often the latest watched
// position is actually SENT, so GPS jitter doesn't spam dispatch.
export const LOCATION_SEND_MS = 7000;

// Used only when the browser denies or lacks geolocation - central
// Hyderabad, not this driver's real position. Sent anyway so dispatch's
// geo-matching has something to match in a demo, and flagged here rather
// than passed off as a real fix.
export const FALLBACK_COORDS = { lat: 17.385, lng: 78.4867 };

/**
 * Broadcasts this driver's position to dispatch while `enabled`, and
 * returns the latest fix for the caller to draw.
 * <p>
 * This is a shared hook rather than an effect on one screen because the
 * driver must keep broadcasting across a navigation. It used to live only
 * in Home, which unmounts when the driver opens a trip - so broadcasting
 * stopped at exactly the moment the customer's tracking map needed it, and
 * their marker sat frozen at wherever the driver was before accepting.
 * <p>
 * The returned position is null until the device reports one. It is the
 * real fix only; the fallback is broadcast but never returned, so a screen
 * never draws central Hyderabad as though it were the driver.
 */
export function useLocationBroadcast(enabled: boolean): { lat: number; lng: number } | null {
  const [position, setPosition] = useState<{ lat: number; lng: number } | null>(null);
  // The sender reads a ref, not state - it must send the newest fix on each
  // tick without the interval being torn down and rebuilt on every update.
  const latest = useRef(FALLBACK_COORDS);

  useEffect(() => {
    if (!enabled) return;
    let watchId: number | null = null;
    if (navigator.geolocation) {
      watchId = navigator.geolocation.watchPosition(
        (pos) => {
          latest.current = { lat: pos.coords.latitude, lng: pos.coords.longitude };
          setPosition(latest.current);
        },
        () => {
          // Keep the last known (or fallback) value - a transient watch
          // error shouldn't stop broadcasting altogether.
        },
        { enableHighAccuracy: true, maximumAge: 10000, timeout: 8000 }
      );
    }
    const send = () => {
      dispatchApi.recordLocation(latest.current.lat, latest.current.lng).catch(() => {});
    };
    send();
    const interval = setInterval(send, LOCATION_SEND_MS);
    return () => {
      if (watchId !== null) navigator.geolocation.clearWatch(watchId);
      clearInterval(interval);
    };
  }, [enabled]);

  return position;
}
