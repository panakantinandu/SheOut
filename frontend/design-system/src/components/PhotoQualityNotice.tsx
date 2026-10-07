import { AlertTriangle, Camera } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from './Button';
import type { PhotoWarning } from '../lib/photoQuality';

/**
 * What the phone noticed about a photo, said plainly, with the two choices
 * she has: take it again, or send it as it is. "Use anyway" is never hidden
 * or greyed out - the phone can be wrong, and a person reads every photo.
 */
export function PhotoQualityNotice({
  warnings,
  onRetake,
  onUseAnyway,
  disabled,
  testId,
}: {
  warnings: PhotoWarning[];
  onRetake: () => void;
  onUseAnyway: () => void;
  disabled?: boolean;
  testId?: string;
}) {
  const { t } = useTranslation('ds');
  if (warnings.length === 0) return null;
  return (
    <div className="space-y-3 rounded-card border border-border bg-surface p-3" role="status" data-testid={testId}>
      <div className="flex items-start gap-2">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-accent-orange" aria-hidden="true" />
        <div className="space-y-1">
          <p className="text-sm font-medium text-text-primary">{t('photoCheck.title')}</p>
          <ul className="space-y-0.5 text-sm text-text-secondary">
            {warnings.map((w) => (
              <li key={w}>{t(`photoCheck.${w}`)}</li>
            ))}
          </ul>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <Button size="md" icon={<Camera className="h-4 w-4" />} disabled={disabled} onClick={onRetake} data-testid={testId ? `${testId}-retake` : undefined}>
          {t('photoCheck.retake')}
        </Button>
        <Button size="md" variant="secondary" disabled={disabled} onClick={onUseAnyway} data-testid={testId ? `${testId}-use-anyway` : undefined}>
          {t('photoCheck.useAnyway')}
        </Button>
      </div>
      <p className="text-caption text-text-secondary">{t('photoCheck.useAnywayHint')}</p>
    </div>
  );
}
