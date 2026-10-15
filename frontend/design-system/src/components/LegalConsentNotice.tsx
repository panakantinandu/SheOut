import { Trans } from 'react-i18next';
export interface LegalConsentNoticeProps {
  /** Kept for callers; the notice no longer names the button (it is not consent). */
  actionLabel?: string;
  onOpenTerms: () => void;
  onOpenPrivacy: () => void;
  className?: string;
}

/** Inline links inside a sentence: at least 24px tall (WCAG 2.2, 2.5.8). */
const LINK = 'inline-block min-h-[24px] py-0.5 font-semibold text-primary underline';

/**
 * The notice on both apps' sign-in screens: where the Terms and the Privacy
 * Policy are, before anything is sent.
 * <p>
 * NOT CONSENT. It used to say "By tapping Send OTP you agree" - consent
 * implied by using the thing she was trying to use. Agreement is now an
 * unticked box she ticks when she creates her account (ConsentCheckbox, on
 * Complete profile), recorded with the version she saw. This line only
 * points at the documents and says she will be asked.
 */
export function LegalConsentNotice({ onOpenTerms, onOpenPrivacy, className }: LegalConsentNoticeProps) {
  return (
    <p className={className ?? 'text-center text-xs leading-relaxed text-text-secondary'}>
      <Trans
        ns="ds"
        i18nKey="consent.text"
        components={{
          terms: <button type="button" onClick={onOpenTerms} className={LINK} />,
          privacy: <button type="button" onClick={onOpenPrivacy} className={LINK} />,
        }}
      />
    </p>
  );
}
