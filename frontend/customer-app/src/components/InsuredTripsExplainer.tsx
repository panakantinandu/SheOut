import { ShieldCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Card, IconCircle, formatSumInsured, useTranslation } from '@sheout/design-system';
import { insuranceApi } from '../api/client';
import type { CoverSummary } from '../api/types';

/**
 * "Every SheOut trip is insured" - in the Safety Center only while it is
 * true: a passenger policy in force right now. With none, nothing is said,
 * not a softer version of the same promise.
 */
export function InsuredTripsExplainer() {
  const { t } = useTranslation();
  const [cover, setCover] = useState<CoverSummary | null>(null);

  useEffect(() => {
    insuranceApi
      .passengerCover()
      .then((c) => setCover(c.active ? c.cover : null))
      .catch(() => setCover(null));
  }, []);

  if (!cover) return null;
  return (
    <Card className="space-y-2" data-testid="insured-trips-explainer">
      <div className="flex items-center gap-3">
        <IconCircle tone="soft" size="sm" color="green" icon={<ShieldCheck />} />
        <h2 className="font-heading text-card-title text-text-primary">{t('safetyCenter.insuredTitle')}</h2>
      </div>
      <p className="text-sm leading-relaxed text-text-secondary">
        {t('safetyCenter.insuredBody', { amount: formatSumInsured(cover.sumInsured), insurer: cover.insurerName })}
      </p>
      <p className="text-sm leading-relaxed text-text-secondary">{t('safetyCenter.insuredClaim')}</p>
    </Card>
  );
}
