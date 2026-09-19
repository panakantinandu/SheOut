import {
  BellRing,
  Bike,
  Gem,
  Gift,
  Hand,
  IndianRupee,
  PackagePlus,
  Scissors,
  Shirt,
  Sparkles,
  UserRound,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  IconCircle,
  SuccessCheck,
  TopHeader,
  brandIllustration,
  showToast,
  useTranslation,
} from '@sheout/design-system';
import { ApiError, preferencesApi } from '../api/client';

const STEPS = [
  { key: 'profile', icon: <UserRound /> },
  { key: 'products', icon: <PackagePlus /> },
  { key: 'orders', icon: <BellRing /> },
  { key: 'paid', icon: <IndianRupee /> },
  { key: 'delivers', icon: <Bike /> },
] as const;

const CATEGORIES = [
  { key: 'fashion', icon: <Shirt />, color: 'primary' },
  { key: 'beauty', icon: <Sparkles />, color: 'orange' },
  { key: 'tailoring', icon: <Scissors />, color: 'green' },
  { key: 'mehandi', icon: <Hand />, color: 'orange' },
  { key: 'gifts', icon: <Gift />, color: 'primary' },
  { key: 'ornaments', icon: <Gem />, color: 'green' },
] as const;

/**
 * SheOut Seller - a teaser for a feature that does not exist yet.
 * <p>
 * Static on purpose: nothing about selling is built. The one thing that
 * works is "Notify me", which records that this account is interested
 * (account + time, nothing else) so there is real evidence of demand before
 * any of it is built. Tapping it twice is still one sign-up.
 */
export function Seller() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [joinedAt, setJoinedAt] = useState<string | null>(null);
  const [checking, setChecking] = useState(true);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    preferencesApi
      .waitlistStatus('seller')
      .then((s) => setJoinedAt(s.joined ? s.joinedAt : null))
      .catch(() => undefined)
      .finally(() => setChecking(false));
  }, []);

  async function notifyMe() {
    setBusy(true);
    try {
      const status = await preferencesApi.joinWaitlist('seller');
      setJoinedAt(status.joinedAt);
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t('seller.notifyError'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('seller.title')} onBack={() => navigate(-1)} />

      <Card variant="primary" className="relative overflow-hidden pb-6">
        <span className="inline-block rounded-full bg-accent-orange px-3 py-1 text-xs font-bold uppercase tracking-widest text-text-inverse">
          {t('seller.comingSoon')}
        </span>
        <div className="relative z-10 mt-3 max-w-[64%]">
          <h1 className="font-heading text-2xl font-bold leading-tight">{t('seller.headline')}</h1>
          <p className="mt-2 text-sm opacity-90">{t('seller.subhead')}</p>
        </div>
        <img
          src={brandIllustration}
          alt=""
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-4 -right-4 h-36 w-36 object-contain opacity-95"
        />
      </Card>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('seller.howItWorks')}</h2>
        <Card className="p-0">
          <ol>
            {STEPS.map((step, index) => (
              <li key={step.key} className="relative flex gap-3 px-4 py-3">
                {/* The line joining the steps, so five rows read as one journey. */}
                {index < STEPS.length - 1 && (
                  <span className="absolute left-[2.1rem] top-12 h-[calc(100%-2.5rem)] w-px bg-primary/20" aria-hidden="true" />
                )}
                <IconCircle tone="soft" icon={step.icon} />
                <div className="min-w-0 flex-1 pt-0.5">
                  <p className="font-heading text-sm font-semibold text-text-primary">
                    <span className="mr-1.5 text-primary">{index + 1}.</span>
                    {t(`seller.steps.${step.key}.title`)}
                  </p>
                  <p className="text-xs text-text-secondary">{t(`seller.steps.${step.key}.body`)}</p>
                </div>
              </li>
            ))}
          </ol>
        </Card>
      </section>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('seller.categoriesTitle')}</h2>
        <div className="grid grid-cols-3 gap-3">
          {CATEGORIES.map((category) => (
            <Card key={category.key} className="flex flex-col items-center gap-2 px-2 py-4 text-center">
              <IconCircle tone="soft" color={category.color} icon={category.icon} />
              <span className="text-xs font-semibold leading-tight text-text-primary">{t(`seller.categories.${category.key}`)}</span>
            </Card>
          ))}
        </div>
      </section>

      {joinedAt ? (
        <Card tone="success" className="flex flex-col items-center gap-2 py-6 text-center" data-testid="seller-joined">
          <SuccessCheck size={56} label={t('seller.joinedTitle')} />
          <p className="font-heading font-semibold text-text-primary">{t('seller.joinedTitle')}</p>
          <p className="text-sm text-text-secondary">{t('seller.joinedBody')}</p>
        </Card>
      ) : (
        <div className="space-y-2">
          <Button
            fullWidth
            icon={<BellRing className="h-4 w-4" />}
            disabled={busy || checking}
            onClick={notifyMe}
            data-testid="seller-notify"
          >
            {busy ? t('seller.notifying') : t('seller.notifyMe')}
          </Button>
          <p className="text-center text-xs text-text-secondary">{t('seller.notifyNote')}</p>
        </div>
      )}
    </div>
  );
}
