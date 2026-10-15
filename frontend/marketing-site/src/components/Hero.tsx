import { useEffect, useRef, useState } from 'react';
import { ArrowRight, BadgeCheck, IndianRupee, Pause, Play, ShieldAlert } from 'lucide-react';
import { HERO, LINKS } from '../content';
import { useInView, usePrefersReducedMotion } from '../lib/motion';
import { SampleLabel } from './SampleLabel';

const CHIP_ICONS = [BadgeCheck, IndianRupee, ShieldAlert];

/**
 * Full-screen hero over the product video. The video is decoration, so it is
 * muted, has no captions to miss, and is hidden from screen readers - but it
 * still gets a visible pause button, never autoplays for someone who asked for
 * less motion (they get the poster), and stops when off screen or in a
 * background tab so it is not burning a phone's battery unseen.
 */
export function Hero() {
  const reduced = usePrefersReducedMotion();
  const section = useRef<HTMLElement>(null);
  const video = useRef<HTMLVideoElement>(null);
  const onScreen = useInView(section);
  const [userPaused, setUserPaused] = useState(false);
  const [playing, setPlaying] = useState(false);

  const shouldPlay = !reduced && !userPaused && onScreen;
  useEffect(() => {
    const v = video.current;
    if (!v) return;
    const sync = () => {
      if (shouldPlay && document.visibilityState === 'visible') v.play().catch(() => setPlaying(false));
      else v.pause();
    };
    sync();
    document.addEventListener('visibilitychange', sync);
    return () => document.removeEventListener('visibilitychange', sync);
  }, [shouldPlay]);

  let word = 0;
  return (
    <section ref={section} id="top" className="relative isolate flex min-h-[100svh] items-end overflow-hidden bg-[#1c0b3d] text-white lg:items-center">
      {/*
        Phones: the video is a band across the top and the words sit below it on
        solid colour. Wide screens: it fills the right two thirds, beside the words.
      */}
      <div className="absolute inset-x-0 top-0 -z-20 h-[50svh] overflow-hidden lg:inset-y-0 lg:left-[33%] lg:h-full">
      <video
        ref={video}
        className="h-full w-full object-cover object-center"
        poster="/media/hero-poster.webp"
        muted
        loop
        playsInline
        preload={reduced ? 'none' : 'auto'}
        aria-hidden="true"
        tabIndex={-1}
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
      >
        <source src="/media/hero-loop.webm" type="video/webm" />
        <source src="/media/hero-loop.mp4" type="video/mp4" />
      </video>
      <SampleLabel className="right-4 top-20 lg:top-24" />
      </div>
      {/* Brand wash: solid where the words are, clear where the phones are. */}
      <div className="absolute inset-0 -z-10 bg-[linear-gradient(to_top,#160735_0%,#160735_48%,rgba(22,7,53,0.55)_70%,rgba(22,7,53,0.35)_100%)] lg:bg-[linear-gradient(to_right,#160735_0%,#160735_32%,rgba(22,7,53,0.7)_48%,rgba(22,7,53,0.15)_75%,rgba(22,7,53,0.35)_100%)]" />
      <div className="absolute -left-40 top-1/4 -z-10 h-[34rem] w-[34rem] rounded-full bg-[#b03cff]/30 blur-[120px] motion-safe:animate-drift" />

      <div className="wrap pb-14 pt-[37svh] sm:pb-20 lg:pb-0 lg:pt-20">
        <div className="max-w-2xl">
          <p className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-4 py-1.5 text-sm font-medium backdrop-blur-md motion-safe:animate-fade-in">
            <span className="relative flex h-2.5 w-2.5">
              <span className="absolute inset-0 rounded-full bg-accent-green motion-safe:animate-pulse-ring" />
              <span className="relative h-2.5 w-2.5 rounded-full bg-accent-green" />
            </span>
            {HERO.eyebrow}
          </p>

          <h1 className="mt-6 text-[2.75rem] font-extrabold leading-[1.02] tracking-tight sm:text-7xl lg:text-[5.25rem]">
            {HERO.lines.map((line, li) => (
              <span key={line} className="block">
                {line.split(' ').map((w) => {
                  const i = word++;
                  return (
                    <span key={w + i} className="inline-block overflow-hidden pb-[0.08em] align-bottom">
                      <span
                        className={`inline-block motion-safe:animate-word-rise ${li === 1 ? 'text-flow-light' : ''}`}
                        style={{ animationDelay: `${150 + i * 90}ms` }}
                      >
                        {w}&nbsp;
                      </span>
                    </span>
                  );
                })}
              </span>
            ))}
          </h1>

          <p className="mt-6 max-w-xl text-lg leading-relaxed text-white/85 motion-safe:animate-rise-in sm:text-xl" style={{ animationDelay: '600ms' }}>
            {HERO.body}
          </p>

          <div className="mt-9 flex flex-col gap-3 motion-safe:animate-rise-in sm:flex-row" style={{ animationDelay: '750ms' }}>
            <a href={LINKS.riderApp} className="btn-light btn-sheen group">
              {HERO.primary}
              <ArrowRight className="h-5 w-5 transition-transform duration-300 group-hover:translate-x-1" aria-hidden />
            </a>
            <a href={LINKS.partnerApp} className="btn-ghost-light">
              {HERO.secondary}
            </a>
          </div>

          <ul className="mt-10 flex flex-wrap gap-x-6 gap-y-3 text-sm text-white/80">
            {HERO.chips.map((c, i) => {
              const Icon = CHIP_ICONS[i];
              return (
                <li key={c} className="flex items-center gap-2 motion-safe:animate-rise-in" style={{ animationDelay: `${900 + i * 110}ms` }}>
                  <Icon className="h-[18px] w-[18px] text-accent-orange" aria-hidden />
                  {c}
                </li>
              );
            })}
          </ul>
        </div>
      </div>

      {!reduced && (
        <button
          type="button"
          onClick={() => setUserPaused((p) => !p)}
          className="absolute bottom-5 right-5 grid h-11 w-11 place-items-center rounded-full border border-white/25 bg-black/25 text-white backdrop-blur-md transition hover:bg-black/40 sm:bottom-8 sm:right-8"
          aria-label={playing ? 'Pause background video' : 'Play background video'}
        >
          {playing ? <Pause className="h-5 w-5" aria-hidden /> : <Play className="h-5 w-5" aria-hidden />}
        </button>
      )}

      <a href="#services" className="absolute bottom-6 left-1/2 hidden -translate-x-1/2 text-white/70 [@media(min-width:1024px)_and_(min-height:980px)]:flex" aria-label="Scroll to services">
        <span className="flex h-9 w-6 justify-center rounded-full border border-white/40 pt-1.5">
          <span className="h-2 w-1 rounded-full bg-white motion-safe:animate-scroll-cue" />
        </span>
      </a>
    </section>
  );
}
