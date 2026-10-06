import { useEffect, useRef, useState, type RefObject } from 'react';

/** True when the visitor asked their device for less movement. Follows changes live. */
export function usePrefersReducedMotion(): boolean {
  const query = '(prefers-reduced-motion: reduce)';
  const [reduced, setReduced] = useState(() => typeof window !== 'undefined' && window.matchMedia(query).matches);
  useEffect(() => {
    const mq = window.matchMedia(query);
    const on = () => setReduced(mq.matches);
    mq.addEventListener('change', on);
    return () => mq.removeEventListener('change', on);
  }, []);
  return reduced;
}

/**
 * Marks every [data-reveal] element with .is-in as it scrolls into view, once.
 * One observer for the whole page rather than one per component.
 */
export function useRevealOnScroll(): void {
  useEffect(() => {
    const nodes = document.querySelectorAll<HTMLElement>('[data-reveal]:not(.is-in)');
    if (!('IntersectionObserver' in window)) {
      nodes.forEach((n) => n.classList.add('is-in'));
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (e.isIntersecting) {
            e.target.classList.add('is-in');
            io.unobserve(e.target);
          }
        }
      },
      { rootMargin: '0px 0px -12% 0px', threshold: 0.12 },
    );
    nodes.forEach((n) => io.observe(n));
    return () => io.disconnect();
  }, []);
}

/** Whether an element is on screen now (not once - it follows it in and out). */
export function useInView<T extends Element>(ref: RefObject<T>, rootMargin = '0px'): boolean {
  const [inView, setInView] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver(([e]) => setInView(e.isIntersecting), { rootMargin });
    io.observe(el);
    return () => io.disconnect();
  }, [ref, rootMargin]);
  return inView;
}

/** Counts from 0 to `to` once `start` turns true, easing out. Lands on the exact number. */
export function useCountUp(to: number, start: boolean, durationMs = 1400): number {
  const reduced = usePrefersReducedMotion();
  const [value, setValue] = useState(0);
  const raf = useRef(0);
  useEffect(() => {
    // Once started it always finishes: scrolling past mid-count must not leave
    // a number stuck halfway (start goes false again when it leaves the screen).
    if (!start || raf.current) return;
    if (reduced || to === 0) {
      setValue(to);
      return;
    }
    const t0 = performance.now();
    const tick = (now: number) => {
      const p = Math.min(1, (now - t0) / durationMs);
      setValue(Math.round(to * (1 - Math.pow(1 - p, 4))));
      raf.current = p < 1 ? requestAnimationFrame(tick) : -1;
    };
    raf.current = requestAnimationFrame(tick);
  }, [start, to, durationMs, reduced]);
  useEffect(
    () => () => {
      cancelAnimationFrame(raf.current);
      raf.current = 0;
    },
    [],
  );
  return value;
}

/** Page scroll position, read at most once a frame. */
export function useScrollY(): number {
  const [y, setY] = useState(0);
  useEffect(() => {
    let raf = 0;
    const on = () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => setY(window.scrollY));
    };
    on();
    window.addEventListener('scroll', on, { passive: true });
    return () => {
      window.removeEventListener('scroll', on);
      cancelAnimationFrame(raf);
    };
  }, []);
  return y;
}

/** Inline style for staggering siblings: <div data-reveal style={stagger(i)}>. */
export const stagger = (i: number, stepMs = 90) => ({ ['--d' as string]: `${i * stepMs}ms` });
