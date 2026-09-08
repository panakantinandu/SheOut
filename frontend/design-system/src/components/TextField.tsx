import { forwardRef, type InputHTMLAttributes, type ReactNode } from 'react';
import { cn } from '../lib/cn';

export interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  icon?: ReactNode;
  label?: string;
  /** Shown under the field in the danger color - e.g. validation errors. */
  error?: string;
}

/**
 * NEW COMPONENT, NOT PART OF THE ORIGINAL REVIEWED SET - flagging per
 * instruction rather than hacking a bare <input> into a screen. Needed by
 * Login (phone/OTP entry) and the delivery intake forms (package/food
 * details) - nothing in the original 8 covers a text input. Same
 * construction pattern as the rest: tokens only (rounded-input, border,
 * primary focus ring), no hardcoded values.
 */
export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(
  ({ icon, label, error, className, id, ...props }, ref) => {
    const inputId = id ?? props.name;
    return (
      <label className="block" htmlFor={inputId}>
        {label && <span className="mb-1.5 block text-sm font-medium text-text-primary">{label}</span>}
        <span
          className={cn(
            'flex h-14 items-center gap-2 rounded-input border bg-surface px-4 transition-colors',
            'focus-within:border-primary',
            error ? 'border-danger' : 'border-border'
          )}
        >
          {icon}
          <input
            ref={ref}
            id={inputId}
            className={cn(
              'h-full flex-1 bg-transparent text-base text-text-primary outline-none placeholder:text-text-secondary',
              className
            )}
            {...props}
          />
        </span>
        {error && <span className="mt-1 block text-xs text-danger">{error}</span>}
      </label>
    );
  }
);
TextField.displayName = 'TextField';
