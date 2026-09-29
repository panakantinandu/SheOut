import { useTheme } from '../lib/theme';
import { useLoadAfterPaint, useRichMotion } from '../lib/richMotion';
import { darkColors } from '../../tokens.js';
import { tokens } from '../tokens';

/**
 * The assistant's name, everywhere it appears. A placeholder until the
 * client picks one; the translations take it as {{name}}.
 */
export const ASSISTANT_NAME = 'SheOut Assistant';

/**
 * The avatar's shape. One constant: changing the assistant's look is this
 * line. Candidates screenshotted for the client: drop, flower, pebble.
 */
const TYPE = 'drop' as const;

/**
 * How the body is lit. `crisp` (a lit rim, clean edge) against `plastic`
 * (the library default, glossy), the Home card's animated avatar at 6x CPU
 * throttle, 2026-09-28: crisp 49 fps with 7 long tasks in 5 s, plastic 37 fps
 * with 11. Crisp is smoother on a slow phone and flatter, closer to the brand.
 */
const SHADING = 'crisp' as const;

export type AssistantAvatarState = 'idle' | 'sleeping';

export interface AssistantAvatarProps {
  /** CSS px. */
  size?: number;
  /** `idle` looks around; `sleeping` is the assistant off for the day (a cap reached). */
  state?: AssistantAvatarState;
  /**
   * Follows a pointer and hops when tapped. Off everywhere except the Home
   * entry card: in a chat or on a help page it would only be something
   * moving while she reads.
   */
  interactive?: boolean;
  /** Frozen on its first frame - calm and still, for the emergency panel and the partner app. */
  still?: boolean;
  /**
   * The flat drop only; the library is never loaded. For the small avatar
   * beside each reply: a canvas per message would cost more than it shows.
   */
  staticOnly?: boolean;
  className?: string;
}

/**
 * The SheOut assistant's face: bot-avatars' animated character, in the
 * brand purple.
 * <p>
 * THE ONLY FILE THAT IMPORTS `bot-avatars`. Replacing the library is a
 * change here and nowhere else.
 * <p>
 * The library is fetched after the first paint (its own chunk, about 25 KB
 * gzipped), with a static SVG of the same size in its place until then, so
 * nothing moves when it arrives. The static SVG is also what shows for good
 * under reduced motion, on a low-end phone, or if the chunk fails to load.
 * <p>
 * Decorative: the name beside it says who is talking, so it is hidden from
 * screen readers.
 */
export function AssistantAvatar({ size = 40, state = 'idle', interactive = false, still = false, staticOnly = false, className }: AssistantAvatarProps) {
  const rich = useRichMotion();
  const [, , theme] = useTheme();
  const lib = useLoadAfterPaint(rich && !staticOnly, () => import('bot-avatars'));
  const color = theme === 'dark' ? darkColors.primary : tokens.colors.primary;

  return (
    <span
      // In rem, so the avatar scales with the text beside it (see theme.css);
      // the canvas and SVG fill it at whatever size that works out to.
      className={['relative flex shrink-0 items-center justify-center overflow-visible [&>canvas]:block [&>canvas]:!h-full [&>canvas]:!w-full', className].filter(Boolean).join(' ')}
      style={{ width: `${size / 16}rem`, height: `${size / 16}rem` }}
      aria-hidden="true"
      data-testid="assistant-avatar"
      data-animated={lib ? 'true' : 'false'}
      data-state={state}
    >
      {lib ? (
        <lib.BotAvatar
          type={TYPE}
          face="mouth"
          state={state === 'sleeping' ? 'sleeping' : 'default'}
          size={size}
          color={color}
          shading={SHADING}
          // The token exactly: the library boosts saturation 1.5x by default, which pushes the purple off-brand.
          saturation={1}
          interactive={interactive}
          paused={still}
          theme={theme === 'dark' ? 'dark' : 'light'}
        />
      ) : (
        <StaticAvatar color={color} sleeping={state === 'sleeping'} />
      )}
    </span>
  );
}

/** The drop, flat, with the same face: what shows until (or instead of) the animated one. */
function StaticAvatar({ color, sleeping }: { color: string; sleeping: boolean }) {
  return (
    <svg width="100%" height="100%" viewBox="0 0 100 100" data-testid="assistant-avatar-static">
      <path d="M50 6C50 6 16 44 16 64a34 34 0 0 0 68 0C84 44 50 6 50 6z" fill={color} />
      {sleeping ? (
        <g stroke="#fff" strokeWidth="4" strokeLinecap="round" fill="none">
          <path d="M36 64q5 4 10 0" />
          <path d="M54 64q5 4 10 0" />
        </g>
      ) : (
        <g fill="#fff">
          <ellipse cx="41" cy="62" rx="4.5" ry="6" />
          <ellipse cx="59" cy="62" rx="4.5" ry="6" />
          <path d="M44 76q6 5 12 0" stroke="#fff" strokeWidth="3.5" strokeLinecap="round" fill="none" />
        </g>
      )}
    </svg>
  );
}
