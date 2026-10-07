import { RefreshCw, WifiOff } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from './Button';

/**
 * A screen's first load failed: said in her language, never the server's
 * own words (which are English, and written for developers), with a way to
 * try again that does not need her to find the back button.
 */
export function LoadFailed({ title, onRetry, testId }: { title: string; onRetry: () => void; testId?: string }) {
  const { t } = useTranslation('ds');
  return (
    <div className="space-y-3 rounded-card border border-border bg-surface p-5 text-center" role="alert" data-testid={testId}>
      <WifiOff className="mx-auto h-8 w-8 text-text-secondary" aria-hidden="true" />
      <p className="font-heading text-card-title text-text-primary">{title}</p>
      <p className="text-sm text-text-secondary">{t('loadFailed.body')}</p>
      <Button fullWidth variant="secondary" icon={<RefreshCw className="h-4 w-4" />} onClick={onRetry} data-testid={testId ? `${testId}-retry` : undefined}>
        {t('loadFailed.retry')}
      </Button>
    </div>
  );
}
