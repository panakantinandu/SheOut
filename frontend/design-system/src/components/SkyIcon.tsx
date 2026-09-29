import { useId, type CSSProperties } from 'react';
import type { DayPart } from '../lib/dayPart';

const own: CSSProperties = { transformBox: 'fill-box', transformOrigin: 'center' };

/**
 * The sky right now, beside the greeting: a sun coming up with its rays
 * turning slowly in the morning, a full sun in the afternoon, a sun going
 * down behind the horizon in the evening, a moon with twinkling stars at
 * night. The same picture anyone reads at a glance, whatever her language.
 * <p>
 * Decorative - the greeting beside it says the time of day in words.
 */
export function SkyIcon({ part, size = 28, className = '' }: { part: DayPart; size?: number; className?: string }) {
  const horizon = `sky-horizon-${useId().replace(/:/g, '')}`;
  const rays = (
    <g stroke="#F59E0B" strokeWidth="4" strokeLinecap="round" className="motion-safe:animate-[spin_14s_linear_infinite]" style={{ transformBox: 'view-box', transformOrigin: '50px 50px' }}>
      {Array.from({ length: 8 }, (_, i) => {
        const a = (i * Math.PI) / 4;
        return <line key={i} x1={50 + Math.cos(a) * 30} y1={50 + Math.sin(a) * 30} x2={50 + Math.cos(a) * 40} y2={50 + Math.sin(a) * 40} />;
      })}
    </g>
  );

  return (
    <span className={`inline-flex shrink-0 ${className}`} style={{ width: `${size / 16}rem`, height: `${size / 16}rem` }} aria-hidden="true" data-testid="sky-icon" data-part={part}>
      <svg viewBox="0 0 100 100" width="100%" height="100%" className="overflow-visible">
        {part === 'morning' && (
          <g className="motion-safe:animate-rise-in">
            {rays}
            <circle cx="50" cy="50" r="21" fill="#FBBF24" />
            <circle cx="44" cy="44" r="7" fill="#FDE68A" opacity="0.8" />
            {/* A small cloud drifting past. */}
            <g className="motion-safe:animate-drift" style={own}>
              <ellipse cx="72" cy="70" rx="16" ry="9" fill="#fff" opacity="0.95" />
              <ellipse cx="62" cy="68" rx="10" ry="8" fill="#fff" opacity="0.95" />
            </g>
          </g>
        )}
        {part === 'afternoon' && (
          <g>
            {rays}
            <circle cx="50" cy="50" r="23" fill="#F59E0B" className="motion-safe:animate-breathe" style={own} />
            <circle cx="43" cy="43" r="7" fill="#FDE68A" opacity="0.7" />
          </g>
        )}
        {part === 'evening' && (
          <g>
            <defs>
              <clipPath id={horizon}>
                <rect x="0" y="0" width="100" height="62" />
              </clipPath>
            </defs>
            <circle cx="50" cy="62" r="34" fill="#FB923C" opacity="0.18" className="motion-safe:animate-breathe" style={own} />
            <g clipPath={`url(#${horizon})`}>
              <circle cx="50" cy="62" r="22" fill="#F97316" />
            </g>
            <line x1="12" y1="63" x2="88" y2="63" stroke="#7C3AED" strokeWidth="4" strokeLinecap="round" />
            <line x1="26" y1="74" x2="74" y2="74" stroke="#7C3AED" strokeWidth="3" strokeLinecap="round" opacity="0.5" />
          </g>
        )}
        {part === 'night' && (
          <g>
            <path d="M62 20 A30 30 0 1 0 80 70 A24 24 0 1 1 62 20 Z" fill="#A78BFA" className="motion-safe:animate-float" />
            {[
              { x: 22, y: 26, r: 4, d: '0s' },
              { x: 82, y: 30, r: 3, d: '0.7s' },
              { x: 30, y: 78, r: 3, d: '1.4s' },
            ].map((s) => (
              <circle key={s.x} cx={s.x} cy={s.y} r={s.r} fill="#FDE68A" className="motion-safe:animate-twinkle" style={{ ...own, animationDelay: s.d }} />
            ))}
          </g>
        )}
      </svg>
    </span>
  );
}
