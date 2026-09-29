import { useEffect, useRef, useState, type ReactNode } from 'react';
import { usePrefersReducedMotion } from '@sheout/design-system';

/**
 * A section that rises into place the first time it scrolls into view, and
 * then stays put - scrolling back up does not replay it.
 * <p>
 * Content is never hidden from anyone who cannot see the movement: under
 * reduced motion, or in a browser without IntersectionObserver, it is shown
 * as it is from the start. Motion never gates reading (see motion.ts).
 */
export function Reveal({ children, delay = 0, className = '' }: { children: ReactNode; delay?: number; className?: string }) {
  const reduced = usePrefersReducedMotion();
  const ref = useRef<HTMLDivElement>(null);
  const [shown, setShown] = useState(() => reduced || typeof IntersectionObserver === 'undefined');

  useEffect(() => {
    if (shown) return;
    if (reduced) {
      setShown(true);
      return;
    }
    const node = ref.current;
    if (!node) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setShown(true);
          observer.disconnect();
        }
      },
      // A little before it is fully on screen, so it is settling as she reaches it.
      { rootMargin: '0px 0px -8% 0px', threshold: 0.12 }
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [shown, reduced]);

  return (
    <div
      ref={ref}
      style={{ transitionDelay: shown ? `${delay}ms` : undefined }}
      className={`transition-[opacity,transform] duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] ${
        shown ? 'translate-y-0 opacity-100' : 'translate-y-5 opacity-0'
      } ${className}`}
    >
      {children}
    </div>
  );
}
