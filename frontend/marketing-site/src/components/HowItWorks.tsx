import { useEffect, useRef, useState } from 'react';
import { STEPS } from '../content';
import { Device } from './Device';
import { SampleLabel } from './SampleLabel';

/**
 * Scroll-told walkthrough. On wide screens the phone stays pinned while the
 * steps scroll past it, and its screen changes to the step in the middle of
 * the viewport. On phones each step simply carries its own screenshot - a
 * pinned phone would leave no room to read.
 */
export function HowItWorks() {
  const [active, setActive] = useState(0);
  const steps = useRef<(HTMLLIElement | null)[]>([]);

  useEffect(() => {
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) if (e.isIntersecting) setActive(Number((e.target as HTMLElement).dataset.step));
      },
      // Only the band across the middle of the screen counts as "this step".
      { rootMargin: '-45% 0px -45% 0px' },
    );
    steps.current.forEach((s) => s && io.observe(s));
    return () => io.disconnect();
  }, []);

  const progress = (active + 1) / STEPS.length;
  return (
    <section id="how" className="relative bg-gradient-to-b from-background via-primary-light/40 to-background py-24 sm:py-32">
      <div className="wrap">
        <div className="max-w-2xl">
          <p className="eyebrow" data-reveal>
            How it works
          </p>
          <h2 className="section-title mt-4" data-reveal>
            From <span className="text-flow">"where to?"</span> to <span className="text-flow">"I'm home"</span> in five steps.
          </h2>
        </div>

        <div className="mt-16 grid gap-12 lg:grid-cols-[1fr_minmax(0,420px)] lg:gap-20">
          <div className="relative">
            {/* The rail: fills as you go down the steps. */}
            <div className="absolute left-[22px] top-2 hidden h-[calc(100%-1rem)] w-0.5 rounded-full bg-primary/10 lg:block" aria-hidden>
              <div
                className="h-full w-full origin-top rounded-full bg-gradient-to-b from-primary-mid to-accent-brand-orange transition-transform duration-700 ease-out-expo"
                style={{ transform: `scaleY(${progress})` }}
              />
            </div>
            <ol className="space-y-16 lg:space-y-0">
            {STEPS.map((s, i) => {
              const on = i === active;
              return (
                <li
                  key={s.title}
                  ref={(el) => (steps.current[i] = el)}
                  data-step={i}
                  className="relative lg:flex lg:min-h-[70vh] lg:items-center lg:pl-20"
                >
                  <div data-reveal>
                    <span
                      className={`mb-5 grid h-11 w-11 place-items-center rounded-full font-heading text-lg font-bold transition-all duration-500 lg:absolute lg:left-0 lg:top-1/2 lg:mb-0 lg:-translate-y-1/2 ${
                        on ? 'bg-primary text-white shadow-[0_0_0_8px_rgb(var(--c-primary-light))] lg:scale-110' : 'bg-white text-primary ring-2 ring-primary/15'
                      }`}
                      aria-hidden
                    >
                      {i + 1}
                    </span>
                    <h3 className={`text-2xl font-bold transition-colors duration-500 sm:text-[2rem] sm:leading-tight ${on ? 'text-text-primary' : 'lg:text-text-primary/40'}`}>
                      <span className="sr-only">Step {i + 1}: </span>
                      {s.title}
                    </h3>
                    <p className={`mt-3 max-w-md text-lg leading-relaxed transition-colors duration-500 ${on ? 'text-text-secondary' : 'text-text-secondary lg:text-text-secondary/50'}`}>
                      {s.body}
                    </p>
                    <Device screen={s.screen} alt={s.alt} className="mx-auto mt-8 w-[min(78vw,300px)] lg:hidden" />
                  </div>
                </li>
              );
            })}
            </ol>
          </div>

          {/* The pinned phone (desktop). Screens cross-fade and settle with a little zoom. */}
          <div className="hidden lg:block">
            <div className="sticky top-[calc(50vh-330px)]">
              <div className="relative mx-auto w-[320px]">
                <div className="absolute -inset-10 -z-10 rounded-full bg-gradient-to-br from-primary-mid/30 via-[#C026D3]/20 to-accent-orange/30 blur-3xl motion-safe:animate-drift-slow" />
                <div className="device relative">
                  <SampleLabel className="left-1/2 top-4 -translate-x-1/2" />
                  <div className="device-screen">
                    {STEPS.map((s, i) => (
                      <img
                        key={s.screen}
                        src={`/screens/${s.screen}.webp`}
                        alt={i === active ? s.alt : ''}
                        aria-hidden={i !== active}
                        loading="lazy"
                        className={`transition-[opacity,transform] duration-700 ease-out-expo ${i === active ? 'scale-100 opacity-100' : 'scale-[1.04] opacity-0'}`}
                      />
                    ))}
                  </div>
                </div>
                <p className="mt-6 text-center text-sm font-medium text-text-secondary">
                  Step {active + 1} of {STEPS.length}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
