import { useEffect, useRef, useState } from 'react';

/**
 * How long both apps hold the splash - the same in the rider and partner
 * apps. It was 3.5 s in one and 2.2 s in the other, which read as the rider
 * app being slow to open.
 */
export const SPLASH_DISPLAY_MS = 2200;
/** The fade out at its end; matches the transition in BrandSplash. */
export const SPLASH_FADE_MS = 300;

const SHOWN_KEY = 'sheout_splash_shown';

/**
 * The splash's clock, shared by both apps' Splash screens.
 * <p>
 * Once per launch. A launch is a fresh browsing session; if the splash has
 * already played in this one - the app reloading itself onto a new version
 * seconds after opening (see appUpdates.ts), which often landed mid-splash -
 * it goes straight on instead of playing a second time.
 * <p>
 * Timed once, from arrival. It used to restart whenever sign-in state
 * changed (read in as the app starts), so it sometimes ran longer than set.
 * `onDone` is read when the time is up, so it acts on the latest state.
 *
 * @returns fading - start the fade; skipped - already shown this launch,
 *          render nothing while it moves on
 */
export function useSplashTimer(onDone: () => void): { fading: boolean; skipped: boolean } {
  const done = useRef(onDone);
  done.current = onDone;
  const [skipped] = useState(() => {
    try {
      return sessionStorage.getItem(SHOWN_KEY) === '1';
    } catch {
      return false;
    }
  });
  const [fading, setFading] = useState(false);

  useEffect(() => {
    if (skipped) {
      done.current();
      return;
    }
    try {
      sessionStorage.setItem(SHOWN_KEY, '1');
    } catch {
      // Storage blocked: it simply shows on every load.
    }
    const fade = window.setTimeout(() => setFading(true), SPLASH_DISPLAY_MS - SPLASH_FADE_MS);
    const next = window.setTimeout(() => done.current(), SPLASH_DISPLAY_MS);
    return () => {
      window.clearTimeout(fade);
      window.clearTimeout(next);
    };
  }, [skipped]);

  return { fading, skipped };
}
