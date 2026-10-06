import { useRef, type PointerEvent } from 'react';
import { ArrowUpRight } from 'lucide-react';
import bikeArt from '../../../design-system/src/assets/art/bike-taxi.webp';
import parcelArt from '../../../design-system/src/assets/art/parcel.webp';
import marketArt from '../../../design-system/src/assets/art/marketplace.webp';
import { LINKS, SERVICES } from '../content';
import { stagger, usePrefersReducedMotion } from '../lib/motion';

const ART = { bike: bikeArt, parcel: parcelArt, market: marketArt };
const HREF = { bike: LINKS.riderApp, parcel: LINKS.riderApp, market: LINKS.sellers };

/** Leans a few degrees toward the pointer. Mouse only - a touch has no hover to follow. */
function TiltCard({ children, className, href }: { children: React.ReactNode; className: string; href: string }) {
  const ref = useRef<HTMLAnchorElement>(null);
  const reduced = usePrefersReducedMotion();
  const move = (e: PointerEvent) => {
    const el = ref.current;
    if (!el || reduced || e.pointerType !== 'mouse') return;
    const r = el.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width - 0.5;
    const y = (e.clientY - r.top) / r.height - 0.5;
    el.style.transform = `perspective(900px) rotateX(${-y * 7}deg) rotateY(${x * 9}deg) translateY(-6px)`;
    el.style.setProperty('--gx', `${(x + 0.5) * 100}%`);
    el.style.setProperty('--gy', `${(y + 0.5) * 100}%`);
  };
  const leave = () => {
    if (ref.current) ref.current.style.transform = '';
  };
  return (
    <a ref={ref} href={href} onPointerMove={move} onPointerLeave={leave} className={className}>
      {children}
    </a>
  );
}

export function Services() {
  return (
    <section id="services" className="relative py-24 sm:py-32">
      <div className="wrap">
        <div className="mx-auto max-w-2xl text-center">
          <p className="eyebrow" data-reveal>
            One app, three ways to get out
          </p>
          <h2 className="section-title mt-4" data-reveal style={stagger(1)}>
            Everything moves with <span className="text-flow">a woman at the wheel.</span>
          </h2>
        </div>

        <div className="mt-16 grid gap-6 md:grid-cols-3">
          {SERVICES.map((s, i) => (
            <div key={s.key} data-reveal style={stagger(i, 120)}>
              <TiltCard
                href={HREF[s.key]}
                className={`group relative flex h-full flex-col overflow-hidden rounded-[28px] bg-gradient-to-b ${s.tint} p-7 shadow-lift ring-1 ring-primary/5 transition-[transform,box-shadow] duration-300 ease-out-expo [transform-style:preserve-3d] hover:shadow-float`}
              >
                {/* Light that follows the pointer across the card. */}
                <span
                  className="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-300 group-hover:opacity-100"
                  style={{ background: 'radial-gradient(420px circle at var(--gx, 50%) var(--gy, 50%), rgba(255,255,255,0.7), transparent 45%)' }}
                  aria-hidden
                />
                <div className="relative mx-auto aspect-square w-full max-w-[260px]">
                  <img
                    src={ART[s.key]}
                    alt=""
                    loading="lazy"
                    className="h-full w-full object-contain drop-shadow-[0_24px_30px_rgba(74,26,158,0.25)] transition-transform duration-500 ease-out-expo group-hover:-translate-y-2 group-hover:scale-[1.04]"
                  />
                </div>
                <p className="relative mt-4 text-sm font-semibold text-primary-mid">{s.tag}</p>
                <h3 className="relative mt-1 text-2xl font-bold">{s.title}</h3>
                <p className="relative mt-3 flex-1 leading-relaxed text-text-secondary">{s.body}</p>
                <span className="relative mt-6 inline-flex items-center gap-1.5 font-heading font-semibold text-primary">
                  {s.key === 'market' ? 'Browse the marketplace' : 'Open the app'}
                  <ArrowUpRight className="h-5 w-5 transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5" aria-hidden />
                </span>
              </TiltCard>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
