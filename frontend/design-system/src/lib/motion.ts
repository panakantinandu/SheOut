import { useEffect, useRef, useState } from 'react';

/**
 * Motion in these two apps, and the one rule that governs all of it.
 * <p>
 * Everything here is CSS animation or a small rAF loop. No animation
 * library was added: the whole set - a press that gives, a shimmer, a
 * depleting ring, a checkmark that draws itself - is a few keyframes and
 * one counter, and a partner on a 3G connection and a three-year-old phone
 * should not pay thirty kilobytes for that. Framer Motion earns its place
 * when layouts animate between states; nothing here does.
 * <p>
 * THE RULE: motion never gates an action. A press animation is CSS on
 * :active, so the tap handler has already fired; a count-up shows a real
 * figure that happens to arrive at itself over half a second; nothing waits
 * for an animation to finish before doing the thing it was asked to do.
 */

/**
 * Whether this person has asked their device for less movement.
 * <p>
 * Honoured completely, not softened: reduced motion means the final state
 * immediately, not a faster animation. Vestibular disorders are not a
 * preference about taste, and the setting is how somebody says so.
 * <p>
 * CSS-driven motion is handled by the global media query in each app's
 * index.css; this hook is for the JavaScript-driven pieces, which the
 * stylesheet cannot reach.
 */
export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(() =>
    typeof window !== 'undefined' && typeof window.matchMedia === 'function'
      ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
      : false
  );

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = () => setReduced(query.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);

  return reduced;
}

/** How long the count-up takes. Long enough to read as movement, short enough that nobody waits for it. */
const COUNT_UP_MS = 500;

/**
 * Counts from zero up to a figure when it first arrives.
 * <p>
 * Only on the first real value, and never again: a balance that re-animates
 * every time a screen refreshes is a number nobody can read, and a fare that
 * counts up while she is deciding whether to book is worse than that.
 * A later change to the figure is shown at once.
 * <p>
 * Returns the figure itself under reduced motion, and while the value is
 * null - there is nothing to count up to yet.
 */
export function useCountUp(value: number | null | undefined, enabled = true): number {
  const reducedMotion = usePrefersReducedMotion();
  const [shown, setShown] = useState<number>(value ?? 0);
  const hasCountedRef = useRef(false);

  useEffect(() => {
    if (value == null) return;
    // Already counted once, asked not to, or nothing to count to: show it.
    if (hasCountedRef.current || !enabled || reducedMotion || value === 0) {
      hasCountedRef.current = true;
      setShown(value);
      return;
    }
    hasCountedRef.current = true;

    let frame = 0;
    const start = performance.now();
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / COUNT_UP_MS);
      // Ease-out: most of the distance early, so the figure is readable
      // almost immediately and only the last digits settle.
      const eased = 1 - Math.pow(1 - progress, 3);
      setShown(value * eased);
      if (progress < 1) frame = requestAnimationFrame(step);
      else setShown(value);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [value, enabled, reducedMotion]);

  return value == null ? 0 : shown;
}
