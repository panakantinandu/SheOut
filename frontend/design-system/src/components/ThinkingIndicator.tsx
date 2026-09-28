import { useTheme } from '../lib/theme';
import { useLoadAfterPaint, useRichMotion } from '../lib/richMotion';
import { darkColors } from '../../tokens.js';
import { tokens } from '../tokens';

export interface ThinkingIndicatorProps {
  /** `searching` while dispatch looks for a partner; `composing` while the assistant writes. */
  mode: 'searching' | 'composing';
  /** 20 inline beside text, 64 on its own. Both are sizes the library hand-tunes. */
  size?: 20 | 64;
  /** Stop on the current frame. */
  paused?: boolean;
  /** What a screen reader says; the animation itself is hidden from it. */
  label: string;
  className?: string;
}

/**
 * Something is happening: thinking-orbs' animated orb, in the brand purple.
 * <p>
 * THE ONLY FILE THAT IMPORTS `thinking-orbs`. Replacing the library is a
 * change here and nowhere else.
 * <p>
 * Fetched after the first paint, with a same-size static ring of dots until
 * then, which is also what stays under reduced motion, on a low-end phone,
 * or if the chunk fails. Light or dark follows the app's own theme setting,
 * not the page's colour scheme, since the two can differ.
 */
export function ThinkingIndicator({ mode, size = 64, paused = false, label, className }: ThinkingIndicatorProps) {
  const rich = useRichMotion();
  const [, , theme] = useTheme();
  const lib = useLoadAfterPaint(rich, () => import('thinking-orbs'));
  const color = theme === 'dark' ? darkColors.primary : tokens.colors.primary;

  return (
    <span
      role="status"
      aria-label={label}
      className={['inline-flex shrink-0 items-center justify-center', className].filter(Boolean).join(' ')}
      style={{ width: size, height: size }}
      data-testid="thinking-indicator"
      data-mode={mode}
      data-animated={lib ? 'true' : 'false'}
    >
      {lib ? (
        <lib.ThinkingOrb state={mode} size={size} theme={theme === 'dark' ? 'dark' : 'light'} color={color} paused={paused} aria-hidden="true" />
      ) : (
        <StaticOrb size={size} color={color} />
      )}
    </span>
  );
}

/** A ring of dots, still: the orb's outline without the motion. */
function StaticOrb({ size, color }: { size: number; color: string }) {
  const dots = size >= 64 ? 12 : 8;
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" aria-hidden="true" data-testid="thinking-indicator-static">
      {Array.from({ length: dots }, (_, i) => {
        const a = (i / dots) * Math.PI * 2;
        return <circle key={i} cx={50 + Math.cos(a) * 34} cy={50 + Math.sin(a) * 34} r={size >= 64 ? 5 : 8} fill={color} opacity={0.35 + 0.65 * (i / dots)} />;
      })}
    </svg>
  );
}
