import { useRef, useState } from 'react';
import { ArrowRight, Play } from 'lucide-react';
import { LINKS, SELLERS } from '../content';
import { stagger } from '../lib/motion';
import { SampleLabel } from './SampleLabel';

/**
 * The seller story, with the apps' own "Sell on SheOut" video. It has sound
 * and a person speaking, so it never plays on its own - only when pressed,
 * with the browser's controls.
 */
export function Sellers() {
  const video = useRef<HTMLVideoElement>(null);
  const [started, setStarted] = useState(false);
  const start = () => {
    setStarted(true);
    requestAnimationFrame(() => video.current?.play().catch(() => {}));
  };

  return (
    <section id="sellers" className="relative py-24 sm:py-32">
      <div className="wrap">
        <div className="relative overflow-hidden rounded-[36px] bg-gradient-to-br from-[#FDF2F8] via-white to-accent-orange-tint p-6 ring-1 ring-primary/5 sm:p-12 lg:p-16">
          <div className="absolute -right-24 -top-24 h-80 w-80 rounded-full bg-[#F0ABFC]/40 blur-3xl" aria-hidden />
          <div className="relative grid items-center gap-12 lg:grid-cols-[1.1fr_1fr]">
            <div>
              <p className="eyebrow !text-[#BE185D]" data-reveal>
                {SELLERS.eyebrow}
              </p>
              <h2 className="section-title mt-4" data-reveal style={stagger(1)}>
                {SELLERS.title}
              </h2>
              <p className="mt-5 max-w-xl text-lg leading-relaxed text-text-secondary" data-reveal style={stagger(2)}>
                {SELLERS.body}
              </p>
              <div className="mt-8 flex flex-wrap items-end gap-x-6 gap-y-3" data-reveal style={stagger(3)}>
                <p className="font-heading text-4xl font-extrabold text-text-primary">{SELLERS.price}</p>
                <p className="pb-1.5 text-text-secondary">{SELLERS.priceNote}</p>
              </div>
              <a href={LINKS.sellers} className="btn-primary btn-sheen group mt-8" data-reveal style={stagger(4)}>
                {SELLERS.cta}
                <ArrowRight className="h-5 w-5 transition-transform duration-300 group-hover:translate-x-1" aria-hidden />
              </a>
            </div>

            <div className="relative mx-auto w-full max-w-[340px]" data-reveal="right">
              <div className="overflow-hidden rounded-[28px] bg-black shadow-[0_40px_80px_-30px_rgba(36,10,80,0.6)] ring-1 ring-black/5">
                <video
                  ref={video}
                  className="aspect-[9/16] w-full object-cover"
                  poster="/media/sell-on-sheout-poster.webp"
                  preload="none"
                  playsInline
                  controls={started}
                >
                  <source src="/media/sell-on-sheout.mp4" type="video/mp4" />
                </video>
                <SampleLabel className="left-3 top-3" />
              </div>
              {!started && (
                <button
                  type="button"
                  onClick={start}
                  className="group absolute inset-0 grid place-items-center rounded-[28px]"
                  aria-label="Play the Sell on SheOut video"
                >
                  <span className="relative grid h-20 w-20 place-items-center rounded-full bg-white/95 text-primary shadow-overlay transition-transform duration-300 ease-out-expo group-hover:scale-110">
                    <span className="absolute inset-0 rounded-full bg-white/60 motion-safe:animate-pulse-ring" />
                    <Play className="relative ml-1 h-8 w-8 fill-current" aria-hidden />
                  </span>
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
