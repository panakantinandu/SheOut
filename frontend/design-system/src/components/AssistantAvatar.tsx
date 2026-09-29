import { useId, type CSSProperties } from 'react';
import { useRichMotion } from '../lib/richMotion';

/**
 * The assistant's name, everywhere it appears. A placeholder until the
 * client picks one; the translations take it as {{name}}.
 */
export const ASSISTANT_NAME = 'SheOut Assistant';

export type AssistantAvatarState = 'idle' | 'sleeping';

export interface AssistantAvatarProps {
  /** CSS px. */
  size?: number;
  /** `idle` looks around; `sleeping` is the assistant off for the day (a cap reached). */
  state?: AssistantAvatarState;
  /**
   * Leans in a little under a pointer or finger. Off everywhere except where
   * the avatar is itself the thing to tap (the Home entry points).
   */
  interactive?: boolean;
  /** Calm and still - for the emergency panel, while a reply is being typed, and the like. */
  still?: boolean;
  /**
   * Never animated: for the small avatar in a menu row or beside each reply,
   * where movement would only distract.
   */
  staticOnly?: boolean;
  className?: string;
}

/** Parts of the drawing turn or scale about their own middle, not the canvas corner. */
const own: CSSProperties = { transformBox: 'fill-box', transformOrigin: 'center' };
/** The head nods from the neck. */
const neck: CSSProperties = { transformBox: 'view-box', transformOrigin: '50px 80px' };

const HAIR = '#2A0F55';
const SKIN = '#F7C9A8';
const ORANGE = '#F59E0B';

/**
 * The SheOut assistant: a woman on the support line - long dark hair, an
 * orange headset with its mic at her cheek - on the brand purple. It says
 * "someone here to help you" in the app's own colours, where the stock
 * blob it replaced said nothing about SheOut.
 * <p>
 * Alive, not busy: she blinks, nods a little as if listening, and the mic
 * glows on and off as though she is on a call. Asleep (the assistant off
 * for the day), her eyes close and she is still. Still, too, when a screen
 * asks for calm (`still`), in menu rows (`staticOnly`), under reduced
 * motion, and on a low-end phone (see useRichMotion).
 * <p>
 * Drawn here in SVG and moved by the shared CSS keyframes (blink, nod,
 * mic-glow): nothing to download, nothing on the main thread, crisp at the
 * 22 px of a menu row and the 64 px of the corner button alike.
 * <p>
 * Decorative: the name beside it says who is talking, so it is hidden from
 * screen readers.
 */
export function AssistantAvatar({ size = 40, state = 'idle', interactive = false, still = false, staticOnly = false, className }: AssistantAvatarProps) {
  const rich = useRichMotion();
  const asleep = state === 'sleeping';
  const moving = rich && !still && !staticOnly && !asleep;
  const on = (cls: string) => (moving ? cls : '');
  // Its own ids, so two avatars on one screen (a menu and the corner button)
  // never borrow each other's gradient from a hidden copy.
  const uid = useId().replace(/:/g, '');
  const bg = `sheout-assistant-bg-${uid}`;
  const clip = `sheout-assistant-clip-${uid}`;

  return (
    <span
      // In rem, so the avatar scales with the text beside it (see theme.css).
      className={[
        'relative flex shrink-0 items-center justify-center overflow-visible',
        interactive ? 'transition-transform duration-200 hover:scale-105 active:scale-95' : '',
        className,
      ].filter(Boolean).join(' ')}
      style={{ width: `${size / 16}rem`, height: `${size / 16}rem` }}
      aria-hidden="true"
      data-testid="assistant-avatar"
      data-animated={moving ? 'true' : 'false'}
      data-state={state}
    >
      <svg width="100%" height="100%" viewBox="0 0 100 100" className="block">
        <defs>
          <radialGradient id={bg} cx="35%" cy="25%" r="85%">
            <stop offset="0%" stopColor="#A06BF7" />
            <stop offset="100%" stopColor="#4A1A9E" />
          </radialGradient>
          <clipPath id={clip}>
            <circle cx="50" cy="50" r="48" />
          </clipPath>
        </defs>
        <circle cx="50" cy="50" r="48" fill={`url(#${bg})`} />

        <g clipPath={`url(#${clip})`}>
          <g className={on('motion-safe:animate-nod')} style={neck}>
            {/* Long hair, falling behind her shoulders. */}
            <path d="M27 58 C24 33 37 21 50 21 C63 21 76 33 73 58 C74 73 69 86 63 92 L37 92 C31 86 26 73 27 58 Z" fill={HAIR} />
            {/* Top and shoulders. */}
            <path d="M20 102 C22 85 36 78 50 78 C64 78 78 85 80 102 Z" fill="#F4EEFF" />
            <path d="M44 78 L50 86 L56 78 Z" fill="#E4D8FF" />
            <rect x="44.5" y="66" width="11" height="13" rx="4" fill={SKIN} />
            {/* Face. */}
            <ellipse cx="50" cy="50" rx="17" ry="19" fill={SKIN} />
            {/* Side-swept fringe. */}
            <path d="M33 47 C32 32 42 26 52 27 C63 28 69 36 67 47 C62 38 54 34 46 37 C40 39 36 43 33 47 Z" fill={HAIR} />
            {/* Cheeks. */}
            <ellipse cx="39.5" cy="57" rx="3.6" ry="2.1" fill="#F472B6" opacity="0.45" />
            <ellipse cx="60.5" cy="57" rx="3.6" ry="2.1" fill="#F472B6" opacity="0.45" />
            {/* Eyes: open and blinking, or shut while she sleeps. */}
            {asleep ? (
              <g stroke={HAIR} strokeWidth="2.2" strokeLinecap="round" fill="none">
                <path d="M40 51 Q43 53.5 46 51" />
                <path d="M54 51 Q57 53.5 60 51" />
              </g>
            ) : (
              <g className={on('motion-safe:animate-blink')} style={own}>
                <ellipse cx="43" cy="50.5" rx="2.4" ry="3.1" fill={HAIR} />
                <ellipse cx="57" cy="50.5" rx="2.4" ry="3.1" fill={HAIR} />
                <circle cx="43.8" cy="49.4" r="0.8" fill="#fff" />
                <circle cx="57.8" cy="49.4" r="0.8" fill="#fff" />
              </g>
            )}
            {/* Smile. */}
            <path d="M45 59 Q50 63.5 55 59" stroke="#8A2F45" strokeWidth="2" strokeLinecap="round" fill="none" />
            {/* The headset: band over her hair, a cup at each ear, the mic at her cheek. */}
            <path d="M30.5 52 C29 29 71 29 69.5 52" stroke={ORANGE} strokeWidth="4" strokeLinecap="round" fill="none" />
            <rect x="26" y="46" width="8" height="13" rx="4" fill={ORANGE} />
            <rect x="66" y="46" width="8" height="13" rx="4" fill={ORANGE} />
            <path d="M30 58 C30.5 64.5 34 67.5 39.5 66.5" stroke={ORANGE} strokeWidth="2.6" strokeLinecap="round" fill="none" />
            <circle cx="40.5" cy="66.3" r="3" fill="#FFC857" className={on('motion-safe:animate-mic-glow')} style={own} />
          </g>
        </g>
      </svg>
    </span>
  );
}
