import { ArrowRight } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { usePrefersReducedMotion } from '@sheout/design-system';

export type HeroSlide = {
  key: string;
  eyebrow: string;
  title: string;
  body: string;
  cta: string;
  art: string;
  /** A fixed brand gradient, not theme tokens: white text has to read on it in light and dark alike. */
  background: string;
  onOpen: () => void;
};

/** How long each slide stays before the next one slides in. */
const DWELL_MS = 5000;
/** After she swipes or taps, the carousel waits this long before moving on its own again. */
const RESUME_AFTER_MS = 7000;

/**
 * Home's banner as a swipeable carousel - one slide per thing SheOut does.
 * <p>
 * It is a native horizontal scroller with scroll snap, so a swipe is the
 * browser's own gesture and feels like every other carousel on the phone;
 * the timer only calls scrollTo. The timer stops while her finger is on it,
 * while the tab is hidden, and entirely under reduced motion, where the
 * slides are still there to swipe. The active dot fills over the dwell time,
 * so the movement is never a surprise.
 */
export function HeroCarousel({ slides, label }: { slides: HeroSlide[]; label: string }) {
  const reduced = usePrefersReducedMotion();
  const track = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);
  const [paused, setPaused] = useState(false);
  const resumeTimer = useRef<number>();

  const goTo = useCallback((index: number) => {
    const node = track.current;
    if (!node) return;
    node.scrollTo({ left: index * node.clientWidth, behavior: reduced ? 'auto' : 'smooth' });
  }, [reduced]);

  // Which slide is showing, from where the scroller actually is.
  useEffect(() => {
    const node = track.current;
    if (!node) return;
    let frame = 0;
    const onScroll = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const index = Math.round(node.scrollLeft / Math.max(1, node.clientWidth));
        setActive(Math.min(slides.length - 1, Math.max(0, index)));
      });
    };
    node.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      node.removeEventListener('scroll', onScroll);
      cancelAnimationFrame(frame);
    };
  }, [slides.length]);

  // Advance on a timer, unless she is holding it, the tab is hidden, or she asked for less movement.
  useEffect(() => {
    if (reduced || paused || slides.length < 2) return;
    const timer = window.setTimeout(() => {
      if (document.visibilityState === 'visible') goTo((active + 1) % slides.length);
    }, DWELL_MS);
    return () => window.clearTimeout(timer);
  }, [active, paused, reduced, slides.length, goTo]);

  useEffect(() => () => window.clearTimeout(resumeTimer.current), []);

  const hold = () => {
    window.clearTimeout(resumeTimer.current);
    setPaused(true);
  };
  const release = () => {
    window.clearTimeout(resumeTimer.current);
    resumeTimer.current = window.setTimeout(() => setPaused(false), RESUME_AFTER_MS);
  };

  const autoplaying = !reduced && !paused && slides.length > 1;

  return (
    <section aria-roledescription="carousel" aria-label={label} className="-mx-screen" data-testid="home-hero">
      <div
        ref={track}
        onPointerDown={hold}
        onPointerUp={release}
        onPointerCancel={release}
        onFocus={hold}
        onBlur={release}
        className="-mb-7 -mt-3 flex snap-x snap-mandatory overflow-x-auto overscroll-x-contain pb-7 pt-3 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        {slides.map((slide, i) => (
          <div
            key={slide.key}
            role="group"
            aria-roledescription="slide"
            aria-label={`${i + 1} / ${slides.length}`}
            aria-hidden={i !== active}
            className="w-full shrink-0 snap-center px-screen"
          >
            <div
              className="relative isolate flex min-h-[10.5rem] overflow-hidden rounded-[1.75rem] p-5 text-white shadow-[0_12px_22px_-10px_rgba(20,10,50,0.55)]"
              style={{ background: slide.background }}
            >
              {/* Two soft lights drifting behind the words - transform only, cheap on any phone. */}
              <span aria-hidden="true" className="pointer-events-none absolute -left-10 -top-12 -z-10 h-40 w-40 rounded-full bg-white/15 blur-2xl motion-safe:animate-drift" />
              <span aria-hidden="true" className="pointer-events-none absolute -bottom-16 right-6 -z-10 h-44 w-44 rounded-full bg-black/15 blur-2xl motion-safe:animate-drift-slow" />
              {/* A band of light crossing now and then. */}
              <span aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-0 -z-10 w-1/3 bg-gradient-to-r from-transparent via-white/20 to-transparent motion-safe:animate-sheen" />

              <div className="relative z-10 flex max-w-[60%] flex-col items-start">
                <span className="rounded-full bg-white/20 px-2.5 py-0.5 text-micro font-semibold uppercase tracking-wide backdrop-blur-sm">
                  {slide.eyebrow}
                </span>
                <p className="mt-2 font-heading text-section leading-tight [text-shadow:0_1px_2px_rgba(0,0,0,0.18)]">{slide.title}</p>
                <p className="mt-1 text-caption opacity-90">{slide.body}</p>
                <button
                  type="button"
                  tabIndex={i === active ? 0 : -1}
                  onClick={slide.onOpen}
                  className="mt-auto inline-flex items-center gap-1.5 rounded-full bg-white px-3.5 py-1.5 text-caption font-semibold text-[#2A1152] shadow-lift transition-transform duration-100 motion-safe:active:scale-95"
                  data-testid={`hero-cta-${slide.key}`}
                >
                  {slide.cta}
                  <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                </button>
              </div>

              <img
                src={slide.art}
                alt=""
                aria-hidden="true"
                draggable={false}
                className="pointer-events-none absolute -right-2 bottom-1 h-36 w-36 select-none object-contain drop-shadow-[0_14px_18px_rgba(0,0,0,0.28)] motion-safe:animate-bob"
              />
            </div>
          </div>
        ))}
      </div>

      {slides.length > 1 && (
        <div className="relative mt-1 flex items-center justify-center gap-1.5">
          {slides.map((slide, i) => (
            <button
              key={slide.key}
              type="button"
              onClick={() => {
                hold();
                goTo(i);
                release();
              }}
              aria-label={`${i + 1} / ${slides.length}`}
              aria-current={i === active}
              className={`relative h-1.5 overflow-hidden rounded-full transition-all duration-300 ${
                i === active ? 'w-6 bg-primary/25' : 'w-1.5 bg-border'
              }`}
            >
              {i === active && (
                <span
                  // Keyed by slide, so the fill restarts each time a slide arrives.
                  key={`${active}-${autoplaying}`}
                  className={`absolute inset-0 origin-left rounded-full bg-primary ${autoplaying ? 'animate-fill-x' : ''}`}
                  style={autoplaying ? { animationDuration: `${DWELL_MS}ms` } : undefined}
                />
              )}
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
