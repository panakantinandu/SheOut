import { useEffect, useRef, useState } from 'react';
import { MotionPatternDetector, type Gesture } from './motionPattern';

/**
 * Discreet SOS: a shake or a double knock on the back of the phone starts
 * the SOS countdown without anyone seeing her open the SOS screen.
 * <p>
 * Limits, said plainly because they matter:
 * <ul>
 *   <li>A web app only receives motion readings while it is open and on
 *       screen. With the phone locked or another app in front nothing is
 *       heard - for that, the phone's own Back Tap / Quick Tap shortcut
 *       opens SheOut's SOS link (/sos?trigger=shortcut), which starts the
 *       same countdown. The Safety Center explains how to set it up.</li>
 *   <li>iPhones ask permission for motion sensors, and only in answer to a
 *       tap. So it is asked while she turns the feature on (introduction or
 *       Safety Center) - never in the middle of an emergency.</li>
 * </ul>
 * It listens only during a trip (see markActiveTrip), when an accidental
 * gesture is least likely to be mistaken and a real one most needed.
 */

const PREF_KEY = 'sheout_discreet_sos';
const TRIP_KEY = 'sheout_active_trip';
/** A trip marker older than this is treated as stale (the app was closed mid-trip, say). */
const TRIP_MAX_AGE_MS = 4 * 60 * 60 * 1000;

type PermissionState = 'granted' | 'denied' | 'unsupported';

interface IosMotionEvent {
  requestPermission?: () => Promise<'granted' | 'denied'>;
}

export function motionSupported(): boolean {
  return typeof window !== 'undefined' && 'DeviceMotionEvent' in window;
}

/** iPhone and iPad: a tap must ask before any reading arrives. */
export function motionNeedsPermission(): boolean {
  return motionSupported() && typeof (window.DeviceMotionEvent as unknown as IosMotionEvent).requestPermission === 'function';
}

/** Call only from a tap. */
export async function requestMotionPermission(): Promise<PermissionState> {
  if (!motionSupported()) return 'unsupported';
  const ask = (window.DeviceMotionEvent as unknown as IosMotionEvent).requestPermission;
  if (typeof ask !== 'function') return 'granted';
  try {
    return (await ask()) === 'granted' ? 'granted' : 'denied';
  } catch {
    return 'denied';
  }
}

export function discreetSosEnabled(): boolean {
  try {
    return localStorage.getItem(PREF_KEY) === 'on';
  } catch {
    return false;
  }
}

export function setDiscreetSosEnabled(on: boolean): void {
  try {
    localStorage.setItem(PREF_KEY, on ? 'on' : 'off');
    window.dispatchEvent(new Event('sheout-discreet-sos'));
  } catch {
    // Storage blocked: it simply stays off.
  }
}

/** Set by the trip screen while a trip is under way, cleared when it ends. */
export function markActiveTrip(bookingId: string | null): void {
  try {
    if (bookingId) localStorage.setItem(TRIP_KEY, JSON.stringify({ bookingId, at: Date.now() }));
    else localStorage.removeItem(TRIP_KEY);
    window.dispatchEvent(new Event('sheout-discreet-sos'));
  } catch {
    // Nothing to mark.
  }
}

export function activeTripId(): string | null {
  try {
    const raw = localStorage.getItem(TRIP_KEY);
    if (!raw) return null;
    const { bookingId, at } = JSON.parse(raw) as { bookingId: string; at: number };
    return Date.now() - at < TRIP_MAX_AGE_MS ? bookingId : null;
  } catch {
    return null;
  }
}

/**
 * Listens for the gestures while discreet SOS is on and a trip is under way.
 * `listening` says whether it is actually hearing the sensor - on an iPhone
 * whose permission lapsed, readings never arrive, and the app can offer a
 * one-tap way to turn it back on.
 */
export function useDiscreetSosListener(onGesture: (gesture: Gesture) => void): {
  armed: boolean;
  listening: boolean;
} {
  const [armed, setArmed] = useState(() => discreetSosEnabled() && activeTripId() !== null);
  const [listening, setListening] = useState(false);
  const callback = useRef(onGesture);
  callback.current = onGesture;

  useEffect(() => {
    const update = () => setArmed(discreetSosEnabled() && activeTripId() !== null);
    window.addEventListener('sheout-discreet-sos', update);
    window.addEventListener('storage', update);
    const timer = window.setInterval(update, 30000);
    return () => {
      window.removeEventListener('sheout-discreet-sos', update);
      window.removeEventListener('storage', update);
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    if (!armed || !motionSupported()) {
      setListening(false);
      return;
    }
    const detector = new MotionPatternDetector();
    // Gravity estimate, for phones that only report acceleration including it.
    const gravity = { x: 0, y: 0, z: 0 };
    let heard = false;
    const onMotion = (e: DeviceMotionEvent) => {
      const linear = e.acceleration;
      let x: number;
      let y: number;
      let z: number;
      if (linear && linear.x != null && linear.y != null && linear.z != null) {
        ({ x, y, z } = linear as { x: number; y: number; z: number });
      } else {
        const g = e.accelerationIncludingGravity;
        if (!g || g.x == null || g.y == null || g.z == null) return;
        gravity.x = 0.9 * gravity.x + 0.1 * g.x;
        gravity.y = 0.9 * gravity.y + 0.1 * g.y;
        gravity.z = 0.9 * gravity.z + 0.1 * g.z;
        x = g.x - gravity.x;
        y = g.y - gravity.y;
        z = g.z - gravity.z;
      }
      if (!heard) {
        heard = true;
        setListening(true);
      }
      const gesture = detector.feed({ t: e.timeStamp || performance.now(), x, y, z });
      if (gesture) callback.current(gesture);
    };
    window.addEventListener('devicemotion', onMotion);
    return () => {
      window.removeEventListener('devicemotion', onMotion);
      setListening(false);
    };
  }, [armed]);

  return { armed, listening };
}
