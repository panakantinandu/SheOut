import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, ReferAFriend, SkeletonCard, TopHeader, useTranslation } from '@sheout/design-system';
import type { ReferralSummary } from '@sheout/design-system';
import { referralsApi } from '../api/client';
import { apiErrorText } from '../lib/apiErrors';

/**
 * Refer a Friend: her own code and invite link, how many partners she brought have driven
 * and what that has paid her, and what the programme gives each
 * side today. Everything on it comes from the server - see ReferralService.
 * A reward is money in her wallet, paid out with her earnings.
 */
export function Refer() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [summary, setSummary] = useState<ReferralSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    referralsApi.mine().then(setSummary).catch((err) => setError(apiErrorText(err, 'refer.loadError')));
  };
  useEffect(load, []);

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('refer.title')} onBack={() => navigate(-1)} />
      {summary ? (
        <ReferAFriend summary={summary} />
      ) : error ? (
        <div className="space-y-3 text-center">
          <p className="text-sm text-danger">{error}</p>
          <Button variant="secondary" onClick={load}>{t('common.tryAgain')}</Button>
        </div>
      ) : (
        <SkeletonCard lines={4} label={t('refer.title')} />
      )}
    </div>
  );
}
