import { TextField } from './TextField';

export interface PickupCodeFieldProps {
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
  /** Fires when four digits are entered and Enter is pressed. */
  onSubmit?: () => void;
}

export const PICKUP_CODE_LENGTH = 4;

/**
 * Where a partner types the code her rider just read out.
 * <p>
 * {@code inputMode="numeric"} so a phone opens the number pad rather than a
 * full keyboard - she is standing at a kerb with one hand on a handlebar,
 * and four taps should be four taps.
 * <p>
 * Non-digits are stripped as she types rather than rejected afterwards.
 * The server counts wrong codes against a limit, so a stray character
 * turning into a failed attempt would cost her one of five tries for a
 * keyboard's autocorrect.
 * <p>
 * {@code autoComplete="off"} and a name that is not "otp" on purpose: this
 * is not the code that was texted to her to sign in, and a browser or
 * password manager offering that one here would be offering the wrong four
 * digits with complete confidence.
 */
export function PickupCodeField({ value, onChange, error, disabled, onSubmit }: PickupCodeFieldProps) {
  return (
    <TextField
      type="text"
      inputMode="numeric"
      autoComplete="off"
      name="pickup-code"
      aria-label="Pickup code"
      placeholder="4-digit code"
      maxLength={PICKUP_CODE_LENGTH}
      className="text-center font-heading text-2xl tracking-[0.5em]"
      value={value}
      disabled={disabled}
      error={error}
      onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, PICKUP_CODE_LENGTH))}
      onKeyDown={(e) => {
        if (e.key === 'Enter' && value.length === PICKUP_CODE_LENGTH) {
          e.preventDefault();
          onSubmit?.();
        }
      }}
    />
  );
}
