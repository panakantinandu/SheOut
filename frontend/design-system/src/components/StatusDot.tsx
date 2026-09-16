import { cn } from '../lib/cn';

export interface StatusDotProps {
  /** Live: the dot is green and breathes. Otherwise it is a quiet grey dot. */
  live: boolean;
  className?: string;
}

/**
 * The dot beside "Online" / "Offline".
 * <p>
 * When she is online a ring expands out of it and fades, every two and a
 * half seconds. Slow on purpose: this says the app is awake and listening
 * for work, which is a reassurance, not an alert. Anything faster reads as
 * something wrong, and a partner glancing at her phone between trips should
 * not have to check.
 * <p>
 * The ring is a second element behind the dot, so only the ring moves - the
 * dot itself stays exactly where it is beside the word.
 */
export function StatusDot({ live, className }: StatusDotProps) {
  return (
    <span className={cn('relative inline-flex h-2 w-2 shrink-0', className)} aria-hidden="true">
      {live && (
        <span className="absolute inset-0 rounded-full bg-accent-green animate-pulse-ring motion-reduce:animate-none motion-reduce:hidden" />
      )}
      <span className={cn('relative h-2 w-2 rounded-full', live ? 'bg-accent-green' : 'bg-text-secondary/40')} />
    </span>
  );
}
