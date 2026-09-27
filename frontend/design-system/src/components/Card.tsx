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
  // Elevation (tokens.js): a surface card rests on the page; the brand card
  // is the screen's hero and floats above it.
  surface: 'text-text-primary shadow-lift',
  primary: 'text-text-inverse shadow-float',
};

const toneBackground: Record<Exclude<CardTone, 'default'>, string> = {
  // Tints, not the base colour at 10% - see COLOUR DEPTH in tokens.js.
  danger: 'bg-accent-red-tint',
  warning: 'bg-accent-orange-tint',
  success: 'bg-accent-green-tint',
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
        // p-5 unless the caller sets its own padding. Both classes on one
        // element do not override each other by order - Tailwind emits p-5
        // after p-0, so p-5 always won and a p-0 list card kept its inset.
        'rounded-card',
        !/(^|\s)p[xytblr]?-/.test(className ?? '') && 'p-5',
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
