import { useEffect, useState } from 'react';
import { usePrefersReducedMotion } from './motion';

/**
 * Whether this device should get the animated assistant avatar and thinking
 * orb, or their static brand-purple stand-ins.
 * <p>
 * Static when she has asked for reduced motion, and on a phone that looks
 * low-end: 2 GB of memory or less, or four cores or fewer, where the browser
 * says so (Safari and Firefox do not report memory, and are judged on what
 * they do report). A canvas animation is decoration; on a cheap phone it
 * costs frames that scrolling and typing need more.
 */
export function useRichMotion(): boolean {
  const reduced = usePrefersReducedMotion();
  return !reduced && !looksLowEnd();
}

export function looksLowEnd(): boolean {
  if (typeof navigator === 'undefined') return true;
  const memory = (navigator as Navigator & { deviceMemory?: number }).deviceMemory;
  const cores = navigator.hardwareConcurrency;
  return (typeof memory === 'number' && memory <= 2) || (typeof cores === 'number' && cores > 0 && cores <= 4);
}

/**
 * Loads a module after the screen has painted, never before: `false` until
 * then, so the static stand-in is what the first paint shows and the
 * library's chunk never competes with the screen's own code.
 * <p>
 * A failed import (offline, a stale deploy) leaves it `null`, and the static
 * stand-in simply stays. It is never retried in a loop.
 */
export function useLoadAfterPaint<T>(enabled: boolean, load: () => Promise<T>): T | null {
  const [loaded, setLoaded] = useState<T | null>(null);
  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    // One frame to paint, then out of the way of whatever the frame started.
    const frame = requestAnimationFrame(() => {
      window.setTimeout(() => {
        load()
          .then((module) => {
            if (!cancelled) setLoaded(() => module);
          })
          .catch(() => undefined);
      }, 0);
    });
    return () => {
      cancelled = true;
      cancelAnimationFrame(frame);
    };
    // `load` is a module-level import thunk in both callers; it never changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);
  return loaded;
}
