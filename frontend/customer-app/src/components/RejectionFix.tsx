import { SafetyText, useTranslation } from '@sheout/design-system';

/**
 * Reasons whose words touch on who she is, her age or fraud: shown with the
 * English beneath until a native speaker has reviewed the translation.
 */
const SAFETY_REASONS = new Set(['WOMAN_NOT_CONFIRMED', 'UNDER_18', 'ID_ALTERED', 'SELFIE_MISMATCH']);

/**
 * Why her ID was sent back, said as what to do about it, in her language -
 * from the reason the reviewer picked from the list. A reason from before
 * the list is shown as it was written.
 */
export function RejectionFix({ code, reason }: { code: string | null | undefined; reason: string | null | undefined }) {
  const { t, i18n } = useTranslation();
  if (code && SAFETY_REASONS.has(code)) return <SafetyText k={`reject.${code}`} />;
  if (code && i18n.exists(`reject.${code}`)) return <>{t(`reject.${code}`)}</>;
  return <>{reason}</>;
}
