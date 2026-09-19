import { useTranslation } from 'react-i18next';

export interface ResendCodeProps {
  /** From useOtpSender. Zero means a new code can be asked for now. */
  secondsUntilResend: number;
  busy?: boolean;
  onResend: () => void;
}

/**
 * The one way to ask for another sign-in code. Counts down the server's
 * cooldown rather than letting her tap into a "please wait" refusal.
 */
export function ResendCode({ secondsUntilResend, busy = false, onResend }: ResendCodeProps) {
  const { t } = useTranslation('ds');
  if (secondsUntilResend > 0) {
    return (
      <p className="text-center text-sm text-text-secondary" data-testid="resend-countdown" aria-live="polite">
        {t('otp.resendIn', { seconds: secondsUntilResend })}
      </p>
    );
  }
  return (
    <p className="text-center text-sm text-text-secondary">
      {t('otp.noCode')}{' '}
      <button
        type="button"
        disabled={busy}
        onClick={onResend}
        className="font-semibold text-primary underline disabled:opacity-60"
        data-testid="resend-code"
      >
        {t('otp.resend')}
      </button>
    </p>
  );
}
