import { Check, Search } from 'lucide-react';

/**
 * The banner line as its points: "Safe rides, verified women partners" is
 * two promises, and run together they broke wherever the column ran out -
 * "Safe rides, verified" over "women partners". Split at the comma (the
 * operator-edited copy and all three languages write it that way), each
 * starts with a capital and gets its own line. A line with no comma stays
 * one line.
 */
function points(body: string): string[] {
  return body
    .split(/[,،]s*/)
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => p.charAt(0).toLocaleUpperCase() + p.slice(1));
}

/**
 * The top of Home: SheOut is a ride app first, and this says so before
 * anything else on the screen.
 * <p>
 * The scooter rides on the spot - a quick small bounce, wind streaking past
 * behind it and the road's centre line running under it. The art carries
 * its own glowing destination pin; a second, animated one used to drop in
 * beside it, and the scene showed two pins for one trip. Under it, "Where
 * are you going?", the bar every ride app opens with, which starts a
 * booking. The title and line come from
 * the operator-edited banner copy.
 * <p>
 * Movement is transform and opacity only, so it costs the compositor and
 * not the CPU on a cheap phone, and the global reduced-motion rule stills
 * all of it; the scene reads the same standing still.
 */
export function RideHero({ eyebrow, title, body, whereTo, art, onBook }: {
  eyebrow: string;
  title: string;
  body: string;
  whereTo: string;
  art: string;
  onBook: () => void;
}) {
  return (
    <section
      className="relative isolate overflow-hidden rounded-[1.75rem] p-4 pt-5 text-white shadow-[0_14px_26px_-12px_rgba(46,14,97,0.7)] motion-safe:animate-rise-in"
      style={{ background: 'linear-gradient(140deg, #8B4DF0 0%, #5B22C4 48%, #2E0E61 100%)' }}
      data-testid="home-hero"
    >
      {/* Soft lights drifting, and a band of light crossing now and then. */}
      <span aria-hidden="true" className="pointer-events-none absolute -left-12 -top-14 -z-10 h-44 w-44 rounded-full bg-white/15 blur-2xl motion-safe:animate-drift" />
      <span aria-hidden="true" className="pointer-events-none absolute -bottom-20 right-0 -z-10 h-52 w-52 rounded-full bg-fuchsia-400/25 blur-3xl motion-safe:animate-drift-slow" />
      <span aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-0 -z-10 w-1/3 bg-gradient-to-r from-transparent via-white/15 to-transparent motion-safe:animate-sheen" />

      <div className="relative min-h-[9.5rem]">
        <div className="relative z-10 flex max-w-[56%] flex-col items-start">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-white/20 px-2.5 py-0.5 text-micro font-semibold uppercase tracking-wide backdrop-blur-sm">
            <span className="relative flex h-1.5 w-1.5">
              <span className="absolute inset-0 rounded-full bg-emerald-300 motion-safe:animate-pulse-ring" />
              <span className="relative h-1.5 w-1.5 rounded-full bg-emerald-300" />
            </span>
            {eyebrow}
          </span>
          <h2 className="mt-2 font-heading text-[1.5rem] leading-[1.15] [text-shadow:0_1px_2px_rgba(0,0,0,0.2)]">{title}</h2>
          <ul className="mt-1.5 space-y-0.5 text-caption">
            {points(body).map((point) => (
              <li key={point} className="flex items-center gap-1.5">
                <Check className="h-3.5 w-3.5 shrink-0 text-emerald-300" strokeWidth={3} aria-hidden="true" />
                <span className="opacity-95">{point}</span>
              </li>
            ))}
          </ul>
        </div>

        {/* The scene: road, wind, scooter. */}
        <div aria-hidden="true" className="pointer-events-none absolute -right-1 -top-1 bottom-0 w-[48%]">
          {/* The road's centre line running under the wheels. */}
          <div className="absolute -right-4 bottom-1 left-0 h-1 overflow-hidden rounded-full [mask-image:linear-gradient(90deg,transparent,#000_30%,#000)]">
            <div
              className="h-full w-[200%] motion-safe:animate-marquee"
              style={{
                animationDuration: '0.9s',
                backgroundImage: 'repeating-linear-gradient(90deg, rgba(255,255,255,0.75) 0 14px, transparent 14px 26px)',
              }}
            />
          </div>

          {/* Wind streaks behind her. */}
          {[
            { top: '38%', width: '42%', delay: '0s' },
            { top: '55%', width: '30%', delay: '-0.45s' },
            { top: '70%', width: '50%', delay: '-0.9s' },
          ].map((line) => (
            <span
              key={line.top}
              className="absolute left-0 h-[3px] origin-right rounded-full bg-gradient-to-r from-transparent via-white/70 to-white/0 motion-safe:animate-speed-line"
              style={{ top: line.top, width: line.width, animationDelay: line.delay }}
            />
          ))}

          <img
            src={art}
            alt=""
            draggable={false}
            className="absolute bottom-2 right-0 h-[8.75rem] w-[8.75rem] select-none rounded-[1.75rem] object-contain drop-shadow-[0_14px_16px_rgba(0,0,0,0.35)] motion-safe:animate-ride"
          />
        </div>
      </div>

      {/* The one thing to do here: say where to. */}
      <button
        type="button"
        onClick={onBook}
        className="group relative z-10 mt-3 flex w-full items-center gap-3 rounded-full bg-white py-2 pl-4 pr-2 text-left shadow-float transition-transform duration-100 motion-safe:active:scale-[0.98]"
        data-testid="home-where-to"
      >
        <span className="relative flex h-2.5 w-2.5 shrink-0" aria-hidden="true">
          <span className="absolute inset-0 rounded-full bg-accent-green motion-safe:animate-pulse-ring" />
          <span className="relative h-2.5 w-2.5 rounded-full bg-accent-green" />
        </span>
        <span className="min-w-0 flex-1 truncate font-heading text-body text-[#2A1152]">{whereTo}</span>
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary text-white transition-transform duration-200 group-hover:scale-105" aria-hidden="true">
          <Search className="h-4 w-4" strokeWidth={2.5} />
        </span>
      </button>
    </section>
  );
}
