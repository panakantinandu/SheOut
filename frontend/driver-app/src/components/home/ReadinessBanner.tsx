import { AlertTriangle, Clock } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, useTranslation } from '@sheout/design-system';
import { usersApi } from '../../api/client';
import type { PartnerReadiness } from '../../api/types';
import { blockerText } from '../../lib/readinessText';

/**
 * The thing standing between her and her next trip, on Home, before she
 * taps Go Online and is refused: "Your vehicle insurance expired on 12 Nov.
 * Upload the new one to go online." - and the button that takes her to fix
 * it. Amber, not red, for something that will block her soon.
 * <p>
 * Read from the same readiness answer going online is decided on. Silent
 * while it loads or if it cannot be read: Home's own refusal still says why.
 * reloadKey refetches it when Home refreshes.
 */
export function ReadinessBanner({ reloadKey }: { reloadKey?: unknown }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [readiness, setReadiness] = useState<PartnerReadiness | null>(null);

  useEffect(() => {
    usersApi.getReadiness().then(setReadiness).catch(() => setReadiness(null));
  }, [reloadKey]);

  if (!readiness) return null;
  const blocker = readiness.blockers[0];
  const warning = readiness.warnings[0];
  if (!blocker && !warning) return null;

  return (
    <Card tone={blocker ? 'danger' : 'warning'} className="flex items-start gap-3" data-testid="readiness-banner">
      {blocker ? <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-danger" /> : <Clock className="mt-0.5 h-5 w-5 shrink-0 text-accent-orange-strong" />}
      <div className="flex-1">
        <p className="text-sm font-semibold text-text-primary">{blocker ? t('docs.home.blockedTitle') : t('docs.home.soonTitle')}</p>
        <p className="mt-1 text-xs text-text-secondary">{blockerText(t, blocker ?? warning)}</p>
        {blocker && readiness.blockers.length > 1 && (
          <p className="mt-1 text-xs text-text-secondary">{t('docs.home.more', { count: readiness.blockers.length - 1 })}</p>
        )}
        <Button size="md" variant={blocker ? 'primary' : 'secondary'} className="mt-3" onClick={() => navigate('/verification')}>
          {t('docs.home.fix')}
        </Button>
      </div>
    </Card>
  );
}
