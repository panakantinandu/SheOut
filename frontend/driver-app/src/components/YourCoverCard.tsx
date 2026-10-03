import { HeartPulse, Phone } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Card, IconCircle, formatSumInsured, useTranslation } from '@sheout/design-system';
import { insuranceApi } from '../api/client';
import type { CoverSummary } from '../api/types';

/**
 * "Your cover": the group covers the insurer has confirmed her in - health,
 * term life, accident - with her member id and how to claim. Nothing at all
 * until one is ENROLLED: being put forward is not being covered, and this
 * card must never say she has cover she does not.
 */
export function YourCoverCard() {
  const { t } = useTranslation();
  const [covers, setCovers] = useState<CoverSummary[]>([]);

  useEffect(() => {
    insuranceApi.myCovers().then(setCovers).catch(() => setCovers([]));
  }, []);

  if (!covers.length) return null;
  return (
    <Card className="space-y-3" data-testid="your-cover">
      <div className="flex items-center gap-3">
        <IconCircle tone="soft" color="green" icon={<HeartPulse />} />
        <p className="font-heading text-card-title text-text-primary">{t('cover.title')}</p>
      </div>
      {covers.map((c) => (
        <div key={`${c.kind}-${c.policyNumber}`} className="space-y-1 rounded-card bg-background p-3 text-xs">
          <p className="text-sm font-semibold text-text-primary">
            {t(`cover.kind.${c.kind}`)} · {formatSumInsured(c.sumInsured)}
          </p>
          <p className="text-text-secondary">{t('cover.with', { insurer: c.insurerName, policy: c.policyNumber })}</p>
          {c.memberId && <p className="text-text-secondary">{t('cover.memberId', { id: c.memberId })}</p>}
          {c.coverageSummary && <p className="whitespace-pre-line text-text-secondary">{c.coverageSummary}</p>}
          {c.claimsPhone && (
            <a href={`tel:${c.claimsPhone}`} className="inline-flex items-center gap-1 font-medium text-primary">
              <Phone className="h-3.5 w-3.5" /> {t('cover.claim', { phone: c.claimsPhone })}
            </a>
          )}
        </div>
      ))}
      <p className="text-xs text-text-secondary">{t('cover.paidBySheOut')}</p>
    </Card>
  );
}
