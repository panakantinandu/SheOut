import type { HTMLAttributes } from 'react';
import { cn } from '../lib/cn';

export type CardVariant = 'surface' | 'primary';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  variant?: CardVariant;
}

const variantClasses: Record<CardVariant, string> = {
  surface: 'bg-surface text-text-primary shadow-card',
  primary: 'bg-gradient-to-br from-primary to-primary-dark text-text-inverse shadow-raised',
};

/** The white (or purple-gradient) rounded-shadow container used everywhere. */
export function Card({ variant = 'surface', className, children, ...props }: CardProps) {
  return (
    <div className={cn('rounded-card p-5', variantClasses[variant], className)} {...props}>
      {children}
    </div>
  );
}
