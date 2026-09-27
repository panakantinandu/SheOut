import { BellRing, IndianRupee, PackagePlus, Store, UserRound } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import {
  Button,
  Card,
  IconCircle,
  ServiceArt,
  SuccessCheck,
  TopHeader,
  showToast,
  useTranslation,
} from '@sheout/design-system';
import { ApiError, preferencesApi } from '../api/client';
import { useAppDrawer } from '../components/AppDrawer';
import fashionArt from '../assets/seller/fashion-saree.webp';
import beautyArt from '../assets/seller/beauty.webp';
import tailoringArt from '../assets/seller/tailoring.webp';
import mehandiArt from '../assets/seller/mehandi.webp';
import giftsArt from '../assets/seller/gifts.webp';
import ornamentsArt from '../assets/seller/ornaments.webp';

/**
 * The six categories, each with its own illustration and a tint from the
 * colour depth scale (tokens.js) behind it. Order is the order a woman
 * browsing would expect: what to wear first, then what to have done, then
 * what to give.
 */
const CATEGORIES = [
  { key: 'fashion', art: fashionArt, tint: 'bg-primary-light' },
  { key: 'beauty', art: beautyArt, tint: 'bg-accent-orange-tint' },
  { key: 'tailoring', art: tailoringArt, tint: 'bg-accent-green-tint' },
  { key: 'mehandi', art: mehandiArt, tint: 'bg-accent-orange-tint' },
  { key: 'gifts', art: giftsArt, tint: 'bg-primary-light' },
  { key: 'ornaments', art: ornamentsArt, tint: 'bg-accent-blue-tint' },
] as const;

const STEPS: { key: string; icon: ReactNode }[] = [
  { key: 'profile', icon: <UserRound /> },
  { key: 'products', icon: <PackagePlus /> },
  { key: 'orders', icon: <BellRing /> },
  { key: 'paid', icon: <IndianRupee /> },
  { key: 'delivers', icon: <ServiceArt kind="ride" size="sm" /> },
];

/**
 * SheOut Seller - a bottom-bar tab for a marketplace that is not built yet.
 * <p>
 * Static on purpose: nothing about selling exists. The one thing that works
 * is "Notify me", which records that this account is interested (account
 * and time, nothing else), so there is real evidence of demand before any of
 * it is built. Tapping it twice is still one sign-up. The page is designed
 * as the flagship it is meant to become - the categories are SheOut's own
 * artwork, and the waitlist is offered in the hero, where the interest is.
 */
export function Seller() {
  const { t } = useTranslation();
  const drawer = useAppDrawer();
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

  const notifyButton = (inverse: boolean) =>
    joinedAt ? (
      <p className={`flex items-center gap-2 text-sm font-semibold ${inverse ? 'text-white' : 'text-accent-green-strong'}`} data-testid="seller-joined-inline">
        <BellRing className="h-4 w-4" aria-hidden="true" />
        {t('seller.onTheList')}
      </p>
    ) : (
      <Button
        size="md"
        variant={inverse ? 'secondary' : 'primary'}
        icon={<BellRing className="h-4 w-4" />}
        disabled={busy || checking}
        onClick={notifyMe}
        className={inverse ? 'border-transparent text-primary' : undefined}
        data-testid={inverse ? 'seller-notify-hero' : 'seller-notify'}
      >
        {busy ? t('seller.notifying') : t('seller.notifyShort')}
      </Button>
    );

  return (
    <div className="space-y-8">
      <TopHeader variant="plain" title={t('seller.title')} onMenuClick={drawer.open} />

      {/* The hero: what it is, that it is coming, and the one thing to do. */}
      <Card variant="primary" className="relative overflow-hidden p-6" data-testid="seller-hero">
        <span className="pointer-events-none absolute -right-16 -top-16 h-48 w-48 rounded-full bg-white/10" aria-hidden="true" />
        <span className="pointer-events-none absolute -bottom-20 right-10 h-40 w-40 rounded-full bg-accent-orange/20" aria-hidden="true" />
        <div className="relative flex items-center gap-2">
          <IconCircle size="sm" icon={<Store />} className="bg-white/15 text-white" />
          <span className="rounded-full bg-accent-orange px-3 py-1 text-caption uppercase tracking-widest text-text-primary">
            {t('seller.comingSoon')}
          </span>
        </div>
        <div className="relative mt-4 max-w-[62%]">
          <h1 className="font-heading text-display">{t('seller.headline')}</h1>
          <p className="mt-2 text-body opacity-90">{t('seller.subhead')}</p>
        </div>
        <div className="pointer-events-none absolute right-2 top-20 h-40 w-36" aria-hidden="true">
          <img src={mehandiArt} alt="" className="absolute right-0 top-0 h-24 w-24 rotate-6 drop-shadow-xl" />
          <img src={fashionArt} alt="" className="absolute left-0 top-10 h-24 w-24 -rotate-6 drop-shadow-xl" />
          <img src={ornamentsArt} alt="" className="absolute bottom-0 right-4 h-20 w-20 rotate-3 drop-shadow-xl" />
        </div>
        <div className="relative mt-6">{notifyButton(true)}</div>
      </Card>

      <section className="space-y-4">
        <div>
          <h2 className="font-heading text-section text-text-primary">{t('seller.categoriesTitle')}</h2>
          <p className="text-caption text-text-secondary">{t('seller.categoriesSub')}</p>
        </div>
        <div className="grid grid-cols-2 gap-4" data-testid="seller-categories">
          {CATEGORIES.map((category) => (
            <Card key={category.key} className="overflow-hidden p-0" data-testid={`seller-category-${category.key}`}>
              <div className={`flex h-32 items-center justify-center ${category.tint}`}>
                <img
                  src={category.art}
                  alt=""
                  aria-hidden="true"
                  loading="lazy"
                  className="h-28 w-28 object-contain drop-shadow-md"
                />
              </div>
              <div className="space-y-1 p-4">
                <p className="font-heading text-card-title text-text-primary">{t(`seller.categories.${category.key}`)}</p>
                <p className="text-caption text-text-secondary">{t(`seller.categoryHints.${category.key}`)}</p>
              </div>
            </Card>
          ))}
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="font-heading text-section text-text-primary">{t('seller.howItWorks')}</h2>
        <Card className="p-0">
          <ol>
            {STEPS.map((step, index) => (
              <li key={step.key} className="relative flex gap-4 px-5 py-4">
                {/* The line joining the steps, so five rows read as one journey. */}
                {index < STEPS.length - 1 && (
                  <span className="absolute bottom-0 left-[2.75rem] top-16 w-px bg-primary-light" aria-hidden="true" />
                )}
                <span className="relative flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-primary-light text-primary [&_svg]:h-5 [&_svg]:w-5">
                  {step.icon}
                  <span className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-primary text-micro leading-none text-text-inverse shadow-lift">
                    {index + 1}
                  </span>
                </span>
                <div className="min-w-0 flex-1 pt-1">
                  <p className="font-heading text-card-title text-text-primary">{t(`seller.steps.${step.key}.title`)}</p>
                  <p className="mt-1 text-caption text-text-secondary">{t(`seller.steps.${step.key}.body`)}</p>
                </div>
              </li>
            ))}
          </ol>
        </Card>
      </section>

      {joinedAt ? (
        <Card tone="success" className="flex flex-col items-center gap-2 py-8 text-center" data-testid="seller-joined">
          <SuccessCheck size={56} label={t('seller.joinedTitle')} />
          <p className="font-heading text-card-title text-text-primary">{t('seller.joinedTitle')}</p>
          <p className="text-body text-text-secondary">{t('seller.joinedBody')}</p>
        </Card>
      ) : (
        <Card className="space-y-4 text-center">
          <p className="font-heading text-section text-text-primary">{t('seller.closingTitle')}</p>
          <div className="flex justify-center">{notifyButton(false)}</div>
          <p className="text-caption text-text-secondary">{t('seller.notifyNote')}</p>
        </Card>
      )}
    </div>
  );
}
