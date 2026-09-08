import type { HTMLAttributes } from 'react';
import { cn } from '../lib/cn';

export type StatusTone = 'success' | 'warning' | 'danger' | 'neutral' | 'primary';

export interface StatusBadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: StatusTone;
}

const toneClasses: Record<StatusTone, string> = {
  success: 'bg-accent-green/15 text-accent-green',
  warning: 'bg-accent-orange/15 text-accent-orange',
  danger: 'bg-danger/15 text-danger',
  neutral: 'bg-border text-text-secondary',
  primary: 'bg-primary-light text-primary',
};

/**
 * Small pill for status words - Completed/Delivered (success), Pending
 * (warning), Cancelled/Declined (danger). The mockup's badges are small
 * enough that it's hard to be certain whether they're a soft pill or just
 * colored text with no background - built as a (subtle) pill since that
 * reads correctly either way; flag this if the original shows plain text.
 */
export function StatusBadge({ tone = 'neutral', className, children, ...props }: StatusBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-chip px-2 py-0.5 text-xs font-medium',
        toneClasses[tone],
        className
      )}
      {...props}
    >
      {children}
    </span>
  );
}
