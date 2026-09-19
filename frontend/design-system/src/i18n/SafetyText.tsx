import { useTranslation } from 'react-i18next';
import { cn } from '../lib/cn';
import { isSafetyReviewed, useAppLanguage } from './index';

/**
 * SafetyText for places that take a plain string - a dialog's message, an
 * error line. Same rule: an unreviewed translation is followed by the
 * English on a new line.
 */
export function useSafetyString(): (k: string, values?: Record<string, unknown>) => string {
  const { t, i18n } = useTranslation('safety');
  const lng = useAppLanguage();
  return (k, values) => {
    const text = t(k, values) as string;
    if (lng === 'en' || isSafetyReviewed(lng)) return text;
    return `${text}\n${i18n.getFixedT('en', 'safety')(k, values) as string}`;
  };
}

export interface SafetyTextProps {
  /** A key in the app's safety namespace. */
  k: string;
  values?: Record<string, unknown>;
  className?: string;
  /** Classes for the English line shown beneath an unreviewed translation. */
  englishClassName?: string;
}

/**
 * A safety-critical sentence - SOS, the pickup code, emergency contacts.
 * <p>
 * The Telugu and Hindi for these were machine-translated as a scaffold and
 * have not been checked by a native speaker. A wrong word in "call for help"
 * or "never share this code" is a safety risk, not a polish issue, so until
 * a language is marked reviewed (safetyReviewed in the app's i18n setup)
 * the English original is shown directly beneath every one of these. She is
 * never left with only the unchecked version. English readers see nothing
 * extra.
 */
export function SafetyText({ k, values, className, englishClassName }: SafetyTextProps) {
  const { t, i18n } = useTranslation('safety');
  const lng = useAppLanguage();
  const text = t(k, values) as string;
  if (lng === 'en' || isSafetyReviewed(lng)) {
    return <span className={className}>{text}</span>;
  }
  const english = i18n.getFixedT('en', 'safety')(k, values) as string;
  return (
    <span className={className}>
      {text}
      <span lang="en" className={cn('mt-0.5 block text-[0.85em] opacity-75', englishClassName)} data-testid="safety-english">
        {english}
      </span>
    </span>
  );
}
