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
  // The screen's main action floats (tokens.js ELEVATION); a secondary one
  // rests. Both press in.
  primary: 'bg-primary text-text-inverse shadow-float hover:bg-primary-dark active:bg-primary-dark',
  secondary: 'bg-surface text-text-primary border border-border shadow-lift hover:bg-background',
  danger: 'bg-danger text-text-inverse shadow-lift hover:brightness-95',
  // The strong shade, not the base: a white label on the base green is 2.7:1.
  success: 'bg-accent-green-strong text-text-inverse shadow-lift hover:brightness-95',
};

const sizeClasses: Record<ButtonSize, string> = {
  md: 'h-11 px-5 text-sm gap-2',
  lg: 'h-14 px-6 text-base gap-3',
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
          'inline-flex items-center justify-center rounded-full font-heading font-semibold',
          // The press. CSS on :active, so the tap handler has already fired
          // by the time anything moves - this can never delay an action,
          // only acknowledge one. Three per cent: felt rather than seen.
          // motion-safe, so somebody who asked for less movement gets none.
          'transition-[color,background-color,transform,box-shadow] duration-100 active:shadow-pressed motion-safe:active:scale-[0.97]',
          // A disabled button is grey, not a paler version of the live one.
          // A washed-out purple pill still reads as "press me" - it was the
          // first thing on the rating sheet and looked broken rather than
          // waiting for an answer. Colour is the difference, not opacity.
          'disabled:pointer-events-none disabled:border-transparent disabled:bg-border disabled:text-text-secondary disabled:shadow-none',
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
