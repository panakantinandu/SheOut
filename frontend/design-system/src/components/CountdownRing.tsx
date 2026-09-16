import { cn } from '../lib/cn';

export interface CountdownRingProps {
  secondsLeft: number;
  /** The window this counts down from, so the ring knows what full looks like. */
  totalSeconds: number;
  size?: number;
  className?: string;
}

/** Below this many seconds the ring turns to the alarm colour. */
const URGENT_FROM_SECONDS = 5;

/**
 * The seconds left on an offer, as a ring that empties.
 * <p>
 * This is the highest-pressure moment a partner has: a fare, two place
 * names, and fifteen seconds to decide. A bare number ticking down makes her
 * read and subtract; a ring is answered at a glance, from across a
 * windscreen mount, while she is looking at the road. The number stays
 * inside it, because "how many seconds" is still the question.
 * <p>
 * The ring is drawn with a stroke dash offset and transitions once per
 * second, so there is no animation loop running beside the timer that
 * already exists - and if motion is reduced, the CSS transition is off and
 * the ring simply steps. Either way it always shows the true remaining time,
 * because nothing here is decorative: this is the clock.
 */
export function CountdownRing({ secondsLeft, totalSeconds, size = 56, className }: CountdownRingProps) {
  const safeTotal = Math.max(1, totalSeconds);
  const clamped = Math.max(0, Math.min(secondsLeft, safeTotal));
  const radius = (size - 6) / 2;
  const circumference = 2 * Math.PI * radius;
  const remaining = clamped / safeTotal;
  const urgent = clamped <= URGENT_FROM_SECONDS;

  return (
    <div
      className={cn('relative inline-flex shrink-0 items-center justify-center', className)}
      style={{ width: size, height: size }}
      role="timer"
      aria-live="off"
      aria-label={`${Math.ceil(clamped)} seconds left to respond`}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true" className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={4}
          className="stroke-border"
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={4}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - remaining)}
          className={cn(
            'transition-[stroke-dashoffset,stroke] duration-1000 ease-linear motion-reduce:transition-none',
            urgent ? 'stroke-danger' : 'stroke-primary'
          )}
        />
      </svg>
      <span
        className={cn(
          'absolute font-heading text-sm font-semibold tabular-nums',
          urgent ? 'text-danger' : 'text-primary'
        )}
      >
        {Math.ceil(clamped)}
      </span>
    </div>
  );
}
