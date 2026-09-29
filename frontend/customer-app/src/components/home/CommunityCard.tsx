import { Heart, HeartHandshake, ShieldCheck, Sparkles } from 'lucide-react';
import type { ReactNode } from 'react';

const PILL_ICONS: ReactNode[] = [<ShieldCheck key="safe" />, <Sparkles key="empowered" />, <HeartHandshake key="together" />];

/**
 * "Women Supporting Women": what SheOut stands for, so it sits just under
 * the services instead of at the foot of Home.
 * <p>
 * The three women rise into the card with small hearts floating up behind
 * them, and the operator's line ("Safe · Empowered · Together") arrives as
 * pills, one after another. The line is split on its dots; if an operator
 * writes it without them it shows as one plain line.
 */
export function CommunityCard({ title, subtitle, art, artAlt }: { title: string; subtitle: string; art: string; artAlt: string }) {
  const parts = subtitle.split('·').map((s) => s.trim()).filter(Boolean);

  return (
    <section
      className="relative isolate flex min-h-[7.5rem] items-center overflow-hidden rounded-[1.75rem] border border-primary/10 bg-primary-light p-4 pr-[44%] shadow-lift"
      data-testid="home-community"
    >
      <span aria-hidden="true" className="pointer-events-none absolute -bottom-16 -right-8 -z-10 h-44 w-44 rounded-full bg-accent-orange/25 blur-2xl motion-safe:animate-drift" />
      <span aria-hidden="true" className="pointer-events-none absolute -left-10 -top-14 -z-10 h-36 w-36 rounded-full bg-primary/15 blur-2xl motion-safe:animate-drift-slow" />

      <div className="relative z-10 min-w-0">
        <h2 className="font-heading text-card-title leading-tight text-primary">{title}</h2>
        {parts.length > 1 ? (
          <ul className="mt-2 flex flex-wrap gap-1.5">
            {parts.map((part, i) => (
              <li
                key={part}
                style={{ animationDelay: `${400 + i * 220}ms` }}
                className="inline-flex items-center gap-1 rounded-full bg-surface px-2 py-0.5 text-micro font-semibold text-text-primary shadow-lift motion-safe:animate-pop-in [&>svg]:h-3 [&>svg]:w-3 [&>svg]:text-primary"
              >
                {PILL_ICONS[i % PILL_ICONS.length]}
                {part}
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-1 text-caption text-text-secondary">{subtitle}</p>
        )}
      </div>

      {/* Hearts floating up behind the three women. */}
      <div aria-hidden="true" className="pointer-events-none absolute bottom-4 right-[18%] -z-10 flex gap-3">
        {[0, 1, 2].map((i) => (
          <Heart
            key={i}
            className={`fill-current text-accent-orange/70 motion-safe:animate-heart-rise ${['h-3 w-3', 'h-4 w-4', 'h-2.5 w-2.5'][i]}`}
            style={{ animationDelay: `${i * 1.05}s` }}
          />
        ))}
      </div>

      <img
        src={art}
        alt={artAlt}
        draggable={false}
        className="absolute bottom-0 right-2 h-[6.75rem] w-[42%] max-w-[11.25rem] select-none object-contain object-right-bottom motion-safe:animate-rise-in"
        style={{ animationDelay: '200ms' }}
      />
    </section>
  );
}
