import { useEffect, useState } from 'react';
import { LOCATION_STALE_MS, type LocationPermission, type LocationStatus } from './LocationBroadcastContext';

/**
 * What the Home status card says about her, as one state.
 * <p>
 * THE RULE: it says "looking for ride requests" only when every link in the
 * chain is really working - the server has her ONLINE, location access is
 * granted, the server accepted a position from this phone within the last
 * LOCATION_STALE_MS, and the phone has a network. Anything less and dispatch
 * may not be able to see her, so the card says what is wrong instead.
 * <ul>
 *   <li>loading - the profile or verification has not arrived yet;</li>
 *   <li>verification - she cannot go online until verified (the existing gate);</li>
 *   <li>offline - the server has her OFFLINE;</li>
 *   <li>noLocation - online, but location access is blocked;</li>
 *   <li>connecting - just gone online, first position on its way (a few seconds);</li>
 *   <li>reconnecting - online, but no network, or no accepted report lately;</li>
 *   <li>live - online, and dispatch can see her.</li>
 * </ul>
 */
export type HeroState = 'loading' | 'verification' | 'offline' | 'noLocation' | 'connecting' | 'reconnecting' | 'live';

export interface HeroInputs {
  profileLoaded: boolean;
  verification: 'loading' | 'verified' | 'pending';
  isOnline: boolean;
  permission: LocationPermission;
  locationStatus: LocationStatus;
  lastReportAt: number | null;
  activeSince: number | null;
  networkUp: boolean;
  now: number;
}

export function deriveHeroState(i: HeroInputs): HeroState {
  if (!i.profileLoaded || i.verification === 'loading') return 'loading';
  if (i.verification === 'pending') return 'verification';
  if (!i.isOnline) return 'offline';
  // A watch that failed while access is granted is a slow or lost GPS fix,
  // not something she can switch on - that is reconnecting, below.
  if (i.permission === 'denied' || (i.locationStatus === 'blocked' && i.permission !== 'granted')) return 'noLocation';
  if (!i.networkUp) return 'reconnecting';
  if (i.lastReportAt != null && i.now - i.lastReportAt <= LOCATION_STALE_MS) return 'live';
  // Just online: the first report gets the same allowance as a missed one,
  // shown as going online rather than as a fault.
  // activeSince is still null for the one render between the server saying
  // ONLINE and the watch starting - that is going online too, not a fault.
  if (i.lastReportAt == null && (i.activeSince == null || i.now - i.activeSince <= LOCATION_STALE_MS)) return 'connecting';
  return 'reconnecting';
}

/** The browser's own view of the network, live. */
export function useNetworkUp(): boolean {
  const [up, setUp] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine));
  useEffect(() => {
    const on = () => setUp(true);
    const off = () => setUp(false);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => {
      window.removeEventListener('online', on);
      window.removeEventListener('offline', off);
    };
  }, []);
  return up;
}

/**
 * The time, re-read every few seconds while `active` - so a report going
 * stale turns the card amber without anything else changing. Idle (no
 * timer at all) while she is offline.
 */
export function useNow(active: boolean, everyMs = 3000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    setNow(Date.now());
    const id = window.setInterval(() => setNow(Date.now()), everyMs);
    return () => window.clearInterval(id);
  }, [active, everyMs]);
  return now;
}

/** Whether the page is on screen. Hidden: stop drawing things nobody can see. */
export function usePageVisible(): boolean {
  const [visible, setVisible] = useState(() => (typeof document === 'undefined' ? true : document.visibilityState === 'visible'));
  useEffect(() => {
    const update = () => setVisible(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', update);
    return () => document.removeEventListener('visibilitychange', update);
  }, []);
  return visible;
}

/** Android, iPhone, or something else - for the location help sheet's wording only. */
export function devicePlatform(): 'android' | 'ios' | 'other' {
  if (typeof navigator === 'undefined') return 'other';
  const ua = navigator.userAgent;
  if (/android/i.test(ua)) return 'android';
  if (/iphone|ipad|ipod/i.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)) return 'ios';
  return 'other';
}
