import { cn } from '../lib/cn';

export interface SuccessCheckProps {
  size?: number;
  className?: string;
  /** Read out when it appears, e.g. "Trip completed". */
  label?: string;
}

/**
 * A checkmark that draws itself, once, when something has finished.
 * <p>
 * Used at the end of a trip in both apps. Deliberately quiet: a circle
 * settling in and a stroke drawing across it, about half a second, in the
 * brand green. No confetti, no bounce. A completed trip is a woman arriving
 * where she was going and a partner getting paid - a piece of somebody's
 * ordinary evening, not a level cleared. Celebration here would also land
 * on the screen shown after a trip that went badly, which is the whole
 * argument against it.
 * <p>
 * Pure SVG and CSS. Under reduced motion the stroke is simply there.
 */
export function SuccessCheck({ size = 64, className, label = 'Done' }: SuccessCheckProps) {
  return (
    <span
      className={cn('inline-flex items-center justify-center', className)}
      role="img"
      aria-label={label}
      style={{ width: size, height: size }}
    >
      <svg viewBox="0 0 52 52" width={size} height={size} aria-hidden="true">
        <circle
          cx="26"
          cy="26"
          r="24"
          className="fill-accent-green/10 stroke-accent-green"
          strokeWidth="2"
          style={{ transformOrigin: 'center' }}
        />
        <path
          d="M15 27.5 L22.5 35 L37.5 19"
          fill="none"
          className="stroke-accent-green animate-draw-check motion-reduce:animate-none"
          strokeWidth="3.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          // 48 is the path length the keyframe retracts; it is also the
          // starting offset, so the stroke is hidden until it draws.
          strokeDasharray="48"
        />
      </svg>
    </span>
  );
}
