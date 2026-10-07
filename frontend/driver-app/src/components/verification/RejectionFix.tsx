import { SafetyText, formatClockTime, useAppLanguage, useTranslation } from '@sheout/design-system';
import type { PartnerDocumentType, VerificationTurnaround } from '../../api/types';

/**
 * Reasons whose words touch on who she is, her age or fraud: shown with the
 * English beneath until a native speaker has reviewed the translation (see
 * SafetyText and the *.safety.json files).
 */
const SAFETY_REASONS = new Set(['WOMAN_NOT_CONFIRMED', 'UNDER_18', 'ID_ALTERED', 'DOCUMENT_ALTERED', 'NOT_HERS', 'SELFIE_MISMATCH']);

/**
 * Why something was sent back, said as what she should do about it, in her
 * language - from the reason the reviewer picked from the list. A reason
 * from before the list is shown as it was written.
 */
export function RejectionFix({
  code,
  reason,
  type,
}: {
  code: string | null | undefined;
  reason: string | null | undefined;
  type?: PartnerDocumentType;
}) {
  const { t, i18n } = useTranslation();
  const document = type ? t(`reject.doc.${type}`) : '';
  if (code && SAFETY_REASONS.has(code)) return <SafetyText k={`reject.${code}`} values={{ document }} />;
  if (code && i18n.exists(`reject.${code}`)) return <>{t(`reject.${code}`, { document })}</>;
  return <>{reason || t('verify.status.rejectedNoReason')}</>;
}

/**
 * "Usually checked within about 2 hours. People check between 9 am and
 * 9 pm, every day." From the measured median of real reviews, or, before
 * there are enough, the target, said as a target.
 */
export function WaitLine({ turnaround }: { turnaround: VerificationTurnaround | null }) {
  const { t } = useTranslation();
  const language = useAppLanguage();
  if (!turnaround) return null;
  const minutes = turnaround.typicalMinutes;
  const time = minutes >= 60
    ? t('verify.waitHours', { count: Math.round(minutes / 60) })
    : t('verify.waitMinutes', { count: minutes });
  return (
    <p className="text-caption text-text-secondary" data-testid="verify-wait">
      {turnaround.measured ? t('verify.waitUsually', { time }) : t('verify.waitTarget', { time })}
      {turnaround.reviewHoursFrom && turnaround.reviewHoursTo && (
        <>
          {' '}
          {t('verify.waitHoursLine', {
            from: formatClockTime(turnaround.reviewHoursFrom, language),
            to: formatClockTime(turnaround.reviewHoursTo, language),
          })}
        </>
      )}
    </p>
  );
}
