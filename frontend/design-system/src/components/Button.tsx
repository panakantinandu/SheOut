import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { cn } from '../lib/cn';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'success';
export type ButtonSize = 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  fullWidth?: boolean;
  icon?: ReactNode;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-text-inverse hover:bg-primary-dark active:bg-primary-dark',
  secondary: 'bg-surface text-text-primary border border-border hover:bg-background',
  danger: 'bg-danger text-text-inverse hover:brightness-95',
  success: 'bg-accent-green text-text-inverse hover:brightness-95',
};

const sizeClasses: Record<ButtonSize, string> = {
  md: 'h-11 px-5 text-sm gap-2',
  lg: 'h-14 px-6 text-base gap-2.5',
};

/**
 * Pill-shaped button - every variant seen in the mockup (Login/Book Now,
 * Continue with Google, SOS/Cancel Ride/Decline, Accept) is fully rounded,
 * never a soft/rectangular radius, so this doesn't take a shape prop.
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'primary', size = 'lg', fullWidth = false, icon, className, children, ...props }, ref) => {
    return (
      <button
        ref={ref}
        className={cn(
          'inline-flex items-center justify-center rounded-full font-heading font-semibold transition-colors',
          'disabled:opacity-50 disabled:pointer-events-none',
          variantClasses[variant],
          sizeClasses[size],
          fullWidth && 'w-full',
          className
        )}
        {...props}
      >
        {icon}
        {children}
      </button>
    );
  }
);
Button.displayName = 'Button';
