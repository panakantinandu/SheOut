import { cn } from '../lib/cn';
import { Card } from './Card';

export interface SkeletonProps {
  className?: string;
}

/**
 * A grey block where content is about to be, lit by a slow shimmer.
 * <p>
 * This replaces the word "Loading..." and the blank screens that preceded
 * content popping in. Both are worse than they look: on a slow connection a
 * centred word gives no sense of what is coming or how much of it, and a
 * blank screen that fills all at once reads as a stall followed by a jump.
 * A skeleton in the shape of the thing being loaded says "this is a list of
 * trips, and it is on its way", and the screen does not move when it lands.
 * <p>
 * Marked aria-hidden with a live region beside it in the callers that need
 * one: a screen reader should hear "loading", not a description of grey boxes.
 */
export function Skeleton({ className }: SkeletonProps) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'block rounded-input bg-border/60',
        // The moving highlight. Sized at 200% so there is something to travel.
        'bg-[linear-gradient(90deg,transparent,rgba(255,255,255,0.65),transparent)] bg-[length:200%_100%] bg-repeat-x',
        'animate-shimmer',
        className
      )}
    />
  );
}

export interface SkeletonListProps {
  /** How many rows to stand in for. Match what the list usually shows, not its maximum. */
  rows?: number;
  /** Row height, in the same shapes the real rows use. */
  className?: string;
  /** A leading circle, for lists whose rows start with an icon or a photo. */
  withIcon?: boolean;
  label?: string;
}

/**
 * The standard stand-in for a list that is loading: a card of rows in the
 * same shape the real ones will take.
 */
export function SkeletonList({ rows = 4, withIcon = true, className, label = 'Loading' }: SkeletonListProps) {
  return (
    <>
      <span className="sr-only" role="status">
        {label}
      </span>
      <Card className={cn('divide-y divide-border p-0', className)}>
        {Array.from({ length: rows }).map((_, index) => (
          <div key={index} className="flex items-center gap-3 p-4">
            {withIcon && <Skeleton className="h-9 w-9 shrink-0 rounded-full" />}
            <div className="min-w-0 flex-1 space-y-2">
              <Skeleton className="h-3.5 w-1/2" />
              <Skeleton className="h-3 w-3/4" />
            </div>
            <Skeleton className="h-3.5 w-12 shrink-0" />
          </div>
        ))}
      </Card>
    </>
  );
}

export interface SkeletonCardProps {
  /** Lines of text to stand in for, under an optional heading block. */
  lines?: number;
  className?: string;
  label?: string;
}

/** The same idea for a single card - a profile, a summary, a form about to arrive. */
export function SkeletonCard({ lines = 3, className, label = 'Loading' }: SkeletonCardProps) {
  return (
    <>
      <span className="sr-only" role="status">
        {label}
      </span>
      <Card className={cn('space-y-3', className)}>
        {Array.from({ length: lines }).map((_, index) => (
          <Skeleton
            key={index}
            className={cn('h-3.5', index === 0 ? 'w-2/5' : index === lines - 1 ? 'w-1/2' : 'w-4/5')}
          />
        ))}
      </Card>
    </>
  );
}
