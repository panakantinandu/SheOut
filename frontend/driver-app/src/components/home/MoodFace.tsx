import { Hourglass, MapPinOff, RefreshCw } from 'lucide-react';
import type { CSSProperties } from 'react';

/**
 * How she is, as a face: the one picture on the status card, readable by
 * anyone without a word of any language - the same faces people send each
 * other every day.
 *
 *   sleepy   offline: eyes shut, a little glum, z's floating up
 *   waking   going online: eyes open, looking left and right, a ring turning
 *   happy    online: a smile, blinking, radar rings going out - "looking for riders"
 *   worried  online but no location: brows up, a frown, a crossed-out pin
 *   unsure   reconnecting: a wobbly mouth, arrows going round
 *   waiting  verification: eyes up, lips pressed, an hourglass turning over
 */
export type Mood = 'sleepy' | 'waking' | 'happy' | 'worried' | 'unsure' | 'waiting';

/** Face colours: the familiar emoji yellow, and a paler lavender for "switched off". */
const SKIN: Record<Mood, [string, string]> = {
  sleepy: ['#F3EEFF', '#C3B4F2'],
  waking: ['#FFF4C2', '#F8C33C'],
  happy: ['#FFF1A8', '#F7B92B'],
  worried: ['#FFF1C7', '#F5B040'],
  unsure: ['#FFF1C7', '#F5B040'],
  waiting: ['#FFF4C2', '#F8C33C'],
};
const INK = '#3A2716';

/** SVG parts rotate and scale about their own middle, not the canvas corner. */
const own: CSSProperties = { transformBox: 'fill-box', transformOrigin: 'center' };

export function MoodFace({ mood, size = 6, className = '' }: { mood: Mood; size?: number; className?: string }) {
  const [light, deep] = SKIN[mood];
  const id = `mood-${mood}`;
  const eyesOpen = mood !== 'sleepy';
  // Where the pupils sit: up at the hourglass while waiting, level otherwise.
  const eyeY = mood === 'waiting' ? 42 : 46;
  const head =
    mood === 'sleepy' ? 'motion-safe:animate-breathe' : mood === 'worried' || mood === 'unsure' ? 'motion-safe:animate-wobble' : '';

  return (
    <span
      className={`relative inline-flex shrink-0 items-center justify-center ${className}`}
      style={{ width: `${size}rem`, height: `${size}rem` }}
      aria-hidden="true"
      data-testid="mood-face"
      data-mood={mood}
    >
      {/* Online: rings going out from her, as a radar does - looking for riders. */}
      {mood === 'happy' &&
        [0, 1].map((i) => (
          <span
            key={i}
            className="absolute inset-[12%] rounded-full border-2 border-accent-green/70 opacity-0 motion-safe:animate-ripple"
            style={{ animationDelay: `${i * 1.2}s` }}
          />
        ))}

      {/* Going online: a dashed ring turning round - working on it. */}
      {mood === 'waking' && (
        <svg viewBox="0 0 100 100" className="absolute inset-0 h-full w-full">
          {/* Turned inside the SVG, so the element's box never grows as it spins. */}
          <circle style={{ transformOrigin: '50px 50px' }} cx="50" cy="50" r="48" fill="none" stroke="currentColor" strokeWidth="3" strokeDasharray="10 9" strokeLinecap="round" className="text-accent-green motion-safe:animate-[spin_3s_linear_infinite]" />
        </svg>
      )}

      <svg viewBox="0 0 100 100" className={`relative h-[82%] w-[82%] drop-shadow-[0_8px_10px_rgba(58,39,22,0.22)] ${head}`} style={own}>
        <defs>
          <radialGradient id={id} cx="38%" cy="32%" r="75%">
            <stop offset="0%" stopColor={light} />
            <stop offset="100%" stopColor={deep} />
          </radialGradient>
        </defs>
        <circle cx="50" cy="50" r="44" fill={`url(#${id})`} />
        {/* Cheeks. */}
        <ellipse cx="27" cy="62" rx="7" ry="4.5" fill="#F472B6" opacity={mood === 'happy' ? 0.45 : 0.25} />
        <ellipse cx="73" cy="62" rx="7" ry="4.5" fill="#F472B6" opacity={mood === 'happy' ? 0.45 : 0.25} />

        {/* Brows: only a worried or unsure face needs them, inner ends raised. */}
        {(mood === 'worried' || mood === 'unsure') && (
          <g stroke={INK} strokeWidth="3.5" strokeLinecap="round">
            <path d="M27 35 L40 30" />
            <path d="M73 35 L60 30" />
          </g>
        )}

        {/* Eyes. */}
        {eyesOpen ? (
          <g className="motion-safe:animate-blink" style={own}>
            <g className={mood === 'waking' ? 'motion-safe:animate-look' : ''} style={own}>
              <circle cx="36" cy={eyeY} r="6" fill={INK} />
              <circle cx="64" cy={eyeY} r="6" fill={INK} />
              <circle cx="38" cy={eyeY - 2} r="2" fill="#fff" />
              <circle cx="66" cy={eyeY - 2} r="2" fill="#fff" />
            </g>
          </g>
        ) : (
          <g stroke={INK} strokeWidth="3.5" strokeLinecap="round" fill="none">
            <path d="M28 46 Q35 52 42 46" />
            <path d="M58 46 Q65 52 72 46" />
          </g>
        )}

        {/* Mouth. */}
        <g stroke={INK} strokeWidth="4" strokeLinecap="round" fill="none">
          {mood === 'sleepy' && <path d="M41 71 Q50 65 59 71" />}
          {mood === 'happy' && <path d="M33 61 Q50 80 67 61" />}
          {mood === 'worried' && <path d="M38 73 Q50 63 62 73" />}
          {mood === 'unsure' && <path d="M35 69 Q40 64 45 69 T55 69 T65 69" />}
          {mood === 'waiting' && <path d="M40 69 L60 69" />}
        </g>
        {mood === 'waking' && <ellipse cx="50" cy="69" rx="5" ry="6" fill={INK} />}
        {/* A bead of sweat, for the worried face. */}
        {mood === 'worried' && <path d="M79 30 Q84 38 79 42 Q74 38 79 30 Z" fill="#60A5FA" />}
      </svg>

      {/* Asleep: z's drifting up and away. */}
      {mood === 'sleepy' &&
        [0, 1, 2].map((i) => (
          <span
            key={i}
            className={`absolute right-[14%] top-[16%] font-heading font-extrabold text-primary opacity-0 motion-safe:animate-zzz motion-reduce:opacity-80 ${i ? 'motion-reduce:hidden' : ''}`}
            style={{ animationDelay: `${i * 1}s`, fontSize: `${0.95 + i * 0.25}rem` }}
          >
            z
          </span>
        ))}

      {/* What the worry is about, in the corner of the face. */}
      {(mood === 'worried' || mood === 'unsure' || mood === 'waiting') && (
        <span className="absolute -bottom-0.5 -right-0.5 flex h-[42%] w-[42%] items-center justify-center rounded-full bg-surface text-accent-orange-strong shadow-float ring-2 ring-accent-orange/40">
          {mood === 'worried' && <MapPinOff className="h-[55%] w-[55%] motion-safe:animate-float" />}
          {mood === 'unsure' && <RefreshCw className="h-[55%] w-[55%] motion-safe:animate-[spin_1.6s_linear_infinite]" />}
          {mood === 'waiting' && <Hourglass className="h-[55%] w-[55%] motion-safe:animate-hourglass-flip" />}
        </span>
      )}
    </span>
  );
}

/** The face for each state of the status card. */
export const MOOD_FOR = {
  offline: 'sleepy',
  connecting: 'waking',
  live: 'happy',
  noLocation: 'worried',
  reconnecting: 'unsure',
  verification: 'waiting',
} as const satisfies Record<string, Mood>;
