import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../lib/cn';

export const OTP_CODE_LENGTH = 6;

export interface OtpCodeFieldProps {
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
  /**
   * Fires when the last digit is typed, and on Enter with a full code.
   * <p>
   * It is handed the finished code, and callers must submit THAT rather
   * than their own state. The state they hold is one render behind at this
   * moment - onChange has been called but React has not re-rendered - so a
   * caller reading its own variable submits five digits of a six-digit
   * code. Every sign-in that autofilled or typed straight through failed
   * with "Validation failed", and pressing Verify by hand then worked,
   * which made it look like the code was wrong rather than truncated.
   */
  onComplete?: (code: string) => void;
}

/**
 * The six digits texted to her, entered one box at a time.
 * <p>
 * It was a single text box with a placeholder. This shows her how many digits
 * are wanted and how many she has, which is the difference between typing and
 * counting - and it is the moment the whole sign-in rests on.
 * <p>
 * ONE REAL INPUT, SIX DRAWN BOXES. The boxes are decoration over a single
 * transparent field. Six separate inputs is the usual way to build this and
 * it breaks the thing that matters most here: Android's SMS autofill fills
 * one field with the whole code, and a paste of "482913" must land as six
 * digits, not one. autoComplete="one-time-code" is what offers the code from
 * the notification in the first place, so a woman signing in never has to
 * leave the app to read her messages.
 * <p>
 * Non-digits are dropped as she types rather than refused afterwards: the
 * server counts wrong codes against a limit, and a keyboard's stray character
 * should not cost her one of those tries.
 */
export function OtpCodeField({ value, onChange, error, disabled, onComplete }: OtpCodeFieldProps) {
  const { t } = useTranslation('ds');
  const inputRef = useRef<HTMLInputElement>(null);
  const [focused, setFocused] = useState(false);
  const digits = Array.from({ length: OTP_CODE_LENGTH }, (_, i) => value[i] ?? '');

  return (
    <div className="space-y-1.5">
      <div className="relative" onClick={() => inputRef.current?.focus()}>
        <input
          ref={inputRef}
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          name="otp"
          aria-label={t('otp.fieldLabel')}
          aria-invalid={Boolean(error)}
          maxLength={OTP_CODE_LENGTH}
          disabled={disabled}
          value={value}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          onChange={(e) => {
            const next = e.target.value.replace(/\D/g, '').slice(0, OTP_CODE_LENGTH);
            onChange(next);
            if (next.length === OTP_CODE_LENGTH) onComplete?.(next);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && value.length === OTP_CODE_LENGTH) {
              e.preventDefault();
              onComplete?.(value);
            }
          }}
          // Over the boxes and invisible: every tap, the caret and the
          // keyboard all belong to the one field that actually holds the code.
          className="absolute inset-0 z-10 h-full w-full cursor-pointer opacity-0"
        />
        <div className="flex justify-between gap-2">
          {digits.map((digit, i) => {
            const active = focused && i === Math.min(value.length, OTP_CODE_LENGTH - 1);
            return (
              <span
                key={i}
                data-testid="otp-box"
                className={cn(
                  'flex h-12 flex-1 items-center justify-center rounded-input border-2 bg-surface font-heading text-xl font-bold transition-colors',
                  error
                    ? 'border-danger text-danger'
                    : digit
                      ? 'border-primary text-primary'
                      : 'border-border text-text-primary',
                  active && !error && 'border-primary ring-4 ring-primary-light'
                )}
              >
                {digit || (active ? <span className="h-5 w-0.5 rounded-full bg-primary motion-safe:animate-caret" /> : '')}
              </span>
            );
          })}
        </div>
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
    </div>
  );
}
