import { Check } from 'lucide-react';
import { forwardRef, useEffect, useRef, useState } from 'react';
import { PHONE_DIGITS, normalizeDigits } from './PhoneField';
import { cn } from '../lib/cn';

/** India's tricolour, drawn - flag emoji show as the letters "IN" on some systems. */
function IndiaFlag() {
  return (
    <svg viewBox="0 0 30 20" className="h-3.5 w-5 overflow-hidden rounded-[3px] ring-1 ring-black/10" aria-hidden="true">
      <rect width="30" height="6.67" fill="#FF9933" />
      <rect y="6.67" width="30" height="6.66" fill="#FFFFFF" />
      <rect y="13.33" width="30" height="6.67" fill="#138808" />
      <circle cx="15" cy="10" r="2.4" fill="none" stroke="#000080" strokeWidth="0.7" />
      <circle cx="15" cy="10" r="0.5" fill="#000080" />
    </svg>
  );
}

/** "98765 43210" - how a mobile number is read out, from the ten digits. */
function spaced(digits: string): string {
  return digits.length > 5 ? `${digits.slice(0, 5)} ${digits.slice(5)}` : digits;
}

export interface PhoneEntryProps {
  /** The ten local digits, without the country code. */
  value: string;
  onChange: (digits: string) => void;
  label: string;
  placeholder?: string;
  error?: string;
  /** Changes each time a submit is refused, to replay the shake. */
  attempt?: number;
  autoFocus?: boolean;
}

/**
 * The phone number at sign-in: the one thing on the screen she has to do,
 * drawn as the centrepiece rather than one field among others.
 * <p>
 * Large, with the country as a chip (India, +91) and the number spaced the
 * way it is read out ("98765 43210"). While she types, a soft ring glows
 * round it and a line beneath fills digit by digit; at ten a tick pops in.
 * A refused submit shakes it once, with the reason under it. Same rules as
 * PhoneField (normalizeDigits: paste "+91 90000 00042" and it arrives
 * whole); the stored value is always the bare ten digits.
 */
export const PhoneEntry = forwardRef<HTMLInputElement, PhoneEntryProps>(
  ({ value, onChange, label, placeholder, error, attempt = 0, autoFocus }, ref) => {
    const [focused, setFocused] = useState(false);
    const [shaking, setShaking] = useState(false);
    const first = useRef(true);
    const complete = value.length === PHONE_DIGITS;
    const progress = value.length / PHONE_DIGITS;

    // A refused submit: shake once.
    useEffect(() => {
      if (first.current) {
        first.current = false;
        return;
      }
      if (!error) return;
      setShaking(true);
      const timer = window.setTimeout(() => setShaking(false), 450);
      return () => window.clearTimeout(timer);
    }, [attempt]); // eslint-disable-line react-hooks/exhaustive-deps

    return (
      <div className="space-y-1.5" data-testid="phone-entry">
        <label htmlFor="sheout-phone" className="block text-sm font-semibold text-text-primary">
          {label}
        </label>
        <div
          className={cn(
            'relative flex h-16 items-center gap-3 overflow-hidden rounded-[1.25rem] border-2 bg-surface px-3 transition-[border-color,box-shadow] duration-200',
            error ? 'border-danger shadow-[0_0_0_4px_rgba(220,38,38,0.12)]' : focused ? 'border-primary shadow-[0_0_0_5px_rgb(var(--c-primary)/0.14)]' : complete ? 'border-accent-green' : 'border-border',
            shaking && 'motion-safe:animate-shake'
          )}
          data-testid="phone-entry-box"
        >
          <span className="flex shrink-0 items-center gap-1.5 rounded-xl bg-primary-light px-2.5 py-1.5 text-sm font-bold text-primary" aria-hidden="true">
            <IndiaFlag />+91
          </span>
          <input
            ref={ref}
            id="sheout-phone"
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            autoFocus={autoFocus}
            value={spaced(value)}
            onChange={(e) => onChange(normalizeDigits(e.target.value))}
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            placeholder={placeholder}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? 'sheout-phone-error' : undefined}
            className="min-w-0 flex-1 bg-transparent font-heading text-[1.3125rem] font-semibold tracking-[0.06em] text-text-primary outline-none placeholder:font-sans placeholder:text-base placeholder:font-normal placeholder:tracking-normal placeholder:text-text-secondary"
            data-testid="phone-entry-input"
          />
          {/* Ten digits: a tick. */}
          <span
            className={cn(
              'flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-accent-green text-white transition-transform duration-300 ease-[cubic-bezier(0.34,1.56,0.64,1)]',
              complete ? 'scale-100' : 'scale-0'
            )}
            aria-hidden="true"
          >
            <Check className="h-4 w-4" strokeWidth={3} />
          </span>
          {/* The line filling digit by digit. */}
          <span aria-hidden="true" className="absolute inset-x-0 bottom-0 h-1 bg-primary/10">
            <span
              className={cn('block h-full origin-left rounded-r-full transition-transform duration-200 ease-out', complete ? 'bg-accent-green' : 'bg-gradient-to-r from-primary to-accent-orange')}
              style={{ transform: `scaleX(${progress})` }}
            />
          </span>
        </div>
        {error && (
          <p id="sheout-phone-error" role="alert" className="text-sm font-medium text-danger motion-safe:animate-fade-slide-in">
            {error}
          </p>
        )}
      </div>
    );
  }
);
PhoneEntry.displayName = 'PhoneEntry';

/**
 * Log in / Sign up as one control: a pill that glides to the side she picks,
 * rather than two buttons swapping colours.
 */
export function AuthModeSwitch<M extends string>({
  modes,
  value,
  onChange,
  label,
  labelFor,
}: {
  modes: readonly M[];
  value: M;
  onChange: (mode: M) => void;
  label: string;
  labelFor: (mode: M) => string;
}) {
  const index = Math.max(0, modes.indexOf(value));
  return (
    <div className="relative grid rounded-full bg-primary-light p-1" style={{ gridTemplateColumns: `repeat(${modes.length}, 1fr)` }} role="tablist" aria-label={label}>
      <span
        aria-hidden="true"
        className="absolute bottom-1 left-1 top-1 rounded-full bg-surface shadow-lift transition-transform duration-300 ease-[cubic-bezier(0.34,1.3,0.64,1)]"
        style={{ width: `calc((100% - 0.5rem) / ${modes.length})`, transform: `translateX(${index * 100}%)` }}
      />
      {modes.map((m) => (
        <button
          key={m}
          type="button"
          role="tab"
          aria-selected={value === m}
          onClick={() => onChange(m)}
          className={cn('relative z-10 rounded-full px-4 py-2 text-sm font-semibold transition-colors duration-200', value === m ? 'text-primary' : 'text-text-secondary')}
          data-testid={`auth-mode-${m}`}
        >
          {labelFor(m)}
        </button>
      ))}
    </div>
  );
}
