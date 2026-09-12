export interface LegalConsentNoticeProps {
  /** What the button beneath it says, so the sentence matches the action. */
  actionLabel: string;
  onOpenTerms: () => void;
  onOpenPrivacy: () => void;
  className?: string;
}

/**
 * The notice shown on both apps' sign-in screens.
 * <p>
 * Placed ABOVE the button, not below it. A consent line underneath the
 * thing it governs is read after the decision, which is no notice at all.
 * <p>
 * Deliberately a notice with links rather than a tick-box you must find and
 * check. There is no separate signup step here - verifying a code both
 * creates and enters an account - so a box would sit on the code screen,
 * after the SMS has already been sent, which is later than the point of
 * collection. Naming the action ("Send OTP") in the sentence, with both
 * documents one tap away before anything is submitted, is the honest
 * version of clickwrap. If the company's counsel wants a recorded, timestamped
 * acceptance instead, that needs a backend field to record it against and a
 * re-prompt when the version changes - a real change, not a checkbox.
 */
export function LegalConsentNotice({
  actionLabel,
  onOpenTerms,
  onOpenPrivacy,
  className,
}: LegalConsentNoticeProps) {
  return (
    <p className={className ?? 'text-center text-xs leading-relaxed text-text-secondary'}>
      By tapping &ldquo;{actionLabel}&rdquo; you agree to our{' '}
      <button type="button" onClick={onOpenTerms} className="font-semibold text-primary underline">
        Terms of Service
      </button>{' '}
      and confirm you have read the{' '}
      <button type="button" onClick={onOpenPrivacy} className="font-semibold text-primary underline">
        Privacy Policy
      </button>
      , including that SheOut is women only and that a government ID is reviewed by a person before your first trip.
    </p>
  );
}
