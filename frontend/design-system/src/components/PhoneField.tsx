import { Phone } from 'lucide-react';
import { forwardRef } from 'react';
import { TextField, type TextFieldProps } from './TextField';

/** SheOut operates in India only; every account is a +91 number. */
export const COUNTRY_CODE = '+91';

/** Indian mobile numbers are exactly ten digits after the country code. */
export const PHONE_DIGITS = 10;

/** The stored/submitted form. The backend only ever sees E.164. */
export function toE164(digits: string): string {
  return `${COUNTRY_CODE}${digits}`;
}

/** Strips anything a user could paste in - spaces, dashes, a leading +91. */
export function normalizeDigits(raw: string): string {
  return raw.replace(/\D/g, '').replace(/^91(?=\d{10}$)/, '').slice(0, PHONE_DIGITS);
}

export function isCompletePhone(digits: string): boolean {
  return new RegExp(`^\\d{${PHONE_DIGITS}}$`).test(digits);
}

export interface PhoneFieldProps
  extends Omit<TextFieldProps, 'value' | 'onChange' | 'type' | 'inputMode' | 'icon'> {
  /** The ten local digits, without the country code. */
  value: string;
  /** Receives the cleaned digits - never raw keystrokes. */
  onChange: (digits: string) => void;
}

/**
 * The one phone input for the whole product.
 * <p>
 * This pattern was written out by hand three separate times - customer
 * Login, driver Login, and the ops console - and the three did not agree.
 * The two apps capped input at ten digits but then validated against a
 * regex permitting seven to fourteen, so a six-digit number passed submit;
 * the ops console accepted any string at all, country code included. A
 * phone field that behaves differently depending on which screen you are
 * standing on is the kind of small inconsistency that makes a product feel
 * unfinished, so the rules now live in one place.
 * <p>
 * The ops console cannot import this - it is a dependency-free static page
 * served by the backend, with no build step and no React - so it mirrors
 * these same rules in plain HTML and JS. That is a deliberate duplicate of
 * behaviour, not of code, and it is noted at the markup there.
 */
export const PhoneField = forwardRef<HTMLInputElement, PhoneFieldProps>(
  ({ value, onChange, placeholder = 'Mobile Number', ...props }, ref) => (
    <TextField
      ref={ref}
      type="tel"
      inputMode="numeric"
      // Deliberately no maxLength: the browser applies it to raw characters
      // before any handler sees them, so pasting "+91 90000 00042" would be
      // cut to "+91 90000 " and arrive as seven digits. normalizeDigits caps
      // the length after stripping punctuation and the country code, which
      // is the only order that survives a pasted number.
      autoComplete="tel-national"
      placeholder={placeholder}
      value={value}
      onChange={(e) => onChange(normalizeDigits(e.target.value))}
      icon={
        <span className="flex items-center gap-2 text-text-secondary">
          <Phone className="h-4 w-4" />
          <span className="h-4 w-px bg-border" />
          <span className="text-sm font-medium text-text-primary">{COUNTRY_CODE}</span>
        </span>
      }
      {...props}
    />
  )
);
PhoneField.displayName = 'PhoneField';
