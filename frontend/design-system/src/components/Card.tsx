import type { HTMLAttributes } from 'react';
import { cn } from '../lib/cn';

export type CardVariant = 'surface' | 'primary';

/**
 * A tinted background for a card that carries a state - a failure, a warning,
 * something highlighted.
 * <p>
 * This is a prop rather than a className because passing `bg-danger/10`
 * alongside the card's own `bg-surface` does not reliably win: cn() is plain
 * clsx with no Tailwind-aware merging, so both utilities reach the class
 * list and stylesheet order decides. It happened to work for the orange
 * tints and not for the red ones, which is exactly the kind of inconsistency
 * nobody can debug from the call site. Splitting the background out means
 * only ever one of them is emitted.
 */
export type CardTone = 'default' | 'danger' | 'warning' | 'success' | 'brand';

const variantBackground: Record<CardVariant, string> = {
  surface: 'bg-surface',
  primary: 'bg-gradient-to-br from-primary to-primary-dark',
};

const variantRest: Record<CardVariant, string> = {
  surface: 'text-text-primary shadow-card',
  primary: 'text-text-inverse shadow-raised',
};

const toneBackground: Record<Exclude<CardTone, 'default'>, string> = {
  danger: 'bg-danger/10',
  warning: 'bg-accent-orange/10',
  success: 'bg-accent-green/10',
  brand: 'bg-primary-light',
};

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  variant?: CardVariant;
  tone?: CardTone;
}

/** The white (or purple-gradient) rounded-shadow container used everywhere. */
export function Card({ variant = 'surface', tone = 'default', className, children, ...props }: CardProps) {
  return (
    <div
      className={cn(
        'rounded-card p-5',
        variantRest[variant],
        tone === 'default' ? variantBackground[variant] : toneBackground[tone],
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}
