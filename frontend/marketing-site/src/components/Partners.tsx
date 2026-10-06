import { useEffect, useRef } from 'react';
import { ArrowRight, Check } from 'lucide-react';
import { LINKS, PARTNERS } from '../content';
import { stagger, usePrefersReducedMotion } from '../lib/motion';
import { Device } from './Device';

/**
 * The three phones drift at different speeds as the section scrolls through,
 * for depth. Written straight to the style in a frame callback - no React
 * render per scroll event.
 */
function useParallax(section: React.RefObject<HTMLElement>, layers: React.RefObject<HTMLDivElement>[], speeds: number[]) {
  const reduced = usePrefersReducedMotion();
  useEffect(() => {
    if (reduced) return;
    let raf = 0;
    const update = () => {
      const el = section.current;
      if (!el) return;
      const r = el.getBoundingClientRect();
      // -1 when the section is just below the screen, 1 when just above.
      const p = Math.max(-1, Math.min(1, (window.innerHeight / 2 - (r.top + r.height / 2)) / (window.innerHeight / 2 + r.height / 2)));
      layers.forEach((l, i) => {
        if (l.current) l.current.style.transform = `translate3d(0, ${(-p * speeds[i]).toFixed(1)}px, 0)`;
      });
    };
    const on = () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(update);
    };
    update();
    window.addEventListener('scroll', on, { passive: true });
    window.addEventListener('resize', on);
    return () => {
      window.removeEventListener('scroll', on);
      window.removeEventListener('resize', on);
      cancelAnimationFrame(raf);
    };
  }, [reduced, section, layers, speeds]);
}

const SPEEDS = [70, 20, 110];

export function Partners() {
  const section = useRef<HTMLElement>(null);
  const a = useRef<HTMLDivElement>(null);
  const b = useRef<HTMLDivElement>(null);
  const c = useRef<HTMLDivElement>(null);
  const layers = useRef([a, b, c]).current;
  useParallax(section, layers, SPEEDS);

  return (
    <section ref={section} id="partners" className="relative overflow-hidden py-24 sm:py-32">
      <div className="wrap grid items-center gap-16 lg:grid-cols-2">
        <div>
          <p className="eyebrow" data-reveal>
            {PARTNERS.eyebrow}
          </p>
          <h2 className="section-title mt-4" data-reveal style={stagger(1)}>
            {PARTNERS.title.split(' ').slice(0, -2).join(' ')} <span className="text-flow">{PARTNERS.title.split(' ').slice(-2).join(' ')}</span>
          </h2>
          <p className="mt-5 max-w-lg text-lg leading-relaxed text-text-secondary" data-reveal style={stagger(2)}>
            {PARTNERS.body}
          </p>
          <ul className="mt-8 space-y-4">
            {PARTNERS.points.map((p, i) => (
              <li key={p} className="flex gap-3" data-reveal style={stagger(3 + i)}>
                <span className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-full bg-accent-green-tint text-accent-green-strong">
                  <Check className="h-4 w-4" strokeWidth={3} aria-hidden />
                </span>
                <span className="text-[17px] leading-relaxed">{p}</span>
              </li>
            ))}
          </ul>
          <p className="mt-8 max-w-lg rounded-2xl bg-primary-light/60 px-5 py-4 text-[15px] leading-relaxed text-text-secondary" data-reveal>
            {PARTNERS.needs}
          </p>
          <a href={LINKS.partnerApp} className="btn-primary btn-sheen group mt-8" data-reveal>
            {PARTNERS.cta}
            <ArrowRight className="h-5 w-5 transition-transform duration-300 group-hover:translate-x-1" aria-hidden />
          </a>
        </div>

        <div className="relative mx-auto h-[560px] w-full max-w-[560px] sm:h-[640px]" data-reveal="zoom">
          <div className="absolute inset-10 -z-10 rounded-full bg-gradient-to-br from-accent-orange/35 via-[#C026D3]/20 to-primary-mid/30 blur-3xl" aria-hidden />
          <div ref={a} className="absolute left-0 top-16 w-[44%] will-change-transform">
            <Device screen="partner-new-ride-request" alt="A new ride request with pickup, drop, distance and the fare, with Accept and Decline" className="-rotate-6" />
          </div>
          <div ref={b} className="absolute left-[26%] top-0 z-10 w-[48%] will-change-transform">
            <Device screen="partner-home-online" alt="Partner home: online, earned 253 rupees today across 4 rides" />
          </div>
          <div ref={c} className="absolute right-0 top-24 w-[44%] will-change-transform">
            <Device screen="partner-payouts" alt="Payouts: available balance and a button to request a payout to her bank or UPI" className="rotate-6" />
          </div>
        </div>
      </div>
    </section>
  );
}
