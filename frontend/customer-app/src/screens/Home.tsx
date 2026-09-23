import { ArrowRight, Clock, MapPinned, ShieldAlert, Wallet as WalletIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  brandIllustration,
  Card,
  IconCircle,
  ListRow,
  PushPromptCard,
  TopHeader,
  contentText,
  useContentSection,
  useAppLanguage,
  usePushNotifications,
  useUnreadNotifications,
} from '@sheout/design-system';
import { OutOfAreaBanner } from '../components/OutOfAreaBanner';
import { UnpaidTripBanner } from '../components/UnpaidTripBanner';
import { useAppDrawer } from '../components/AppDrawer';
import { PUSH_TOKEN_KEY, contentApi, notificationsApi, pushApi, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useTranslation } from '@sheout/design-system';
import bikeTaxiImage from '../../../../public/BikeTaxiImage.png';
import parcelImage from '../../../../public/ParcelImage.png';
import womenImage from '../../../../public/Women.png';

/**
 * Two of the mockup's three service tiles - Lunch Box is deferred for
 * launch. Kept as data rather than repeated markup so restoring the third
 * is one entry, not another copy of the tile.
 */
const SERVICES = [
  { key: 'ride', labelKey: 'home.serviceRide', to: '/book/ride', bg: 'bg-primary', image: bikeTaxiImage, alt: 'Bike Taxi' },
  { key: 'parcel', labelKey: 'home.serviceParcel', to: '/book/parcel', bg: 'bg-accent-orange', image: parcelImage, alt: 'Parcel Delivery' },
];

/**
 * Real data: the greeting name comes from users' live GET /me. The banner and
 * the community card are editable copy from the content module - operators
 * change them in the ops console - with the text they were seeded with as the
 * fallback. Service tiles and quick access are still static UI.
 */
export function Home() {
  const { t } = useTranslation();
  const lng = useAppLanguage();
  const drawer = useAppDrawer();
  // Operators edit the English banners in the console; other languages use
  // the translated defaults until the content module has per-language copy.
  const fromContent = (key: string, fallbackKey: string) => (lng === 'en' ? contentText(copy, key, t(fallbackKey)) : t(fallbackKey));
  const navigate = useNavigate();
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const copy = useContentSection('home.', contentApi.getSection);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then(setProfile)
      .catch(() => setProfile(null))
      .finally(() => setProfileLoading(false));
  }, []);

  const firstName = profile?.name?.split(' ')[0];
  // Blank while the profile fetch is in flight, rather than "Hello, there" -
  // that fallback is only correct once we actually know the name is
  // missing, not while we simply don't know yet (was flashing briefly for
  // every user, including ones with a saved name, before the fetch resolved).
  const greeting = profileLoading ? '' : t('home.greeting', { name: firstName || t('home.there') });
  // Straight after sign-in this is the first screen she sees, so this is
  // where the one notification-permission prompt appears.
  const push = usePushNotifications(pushApi, PUSH_TOKEN_KEY, true);
  const unreadCount = useUnreadNotifications(notificationsApi.unreadCount, true);

  return (
    <div className="space-y-6">
      <TopHeader
        variant="greeting"
        title={greeting}
        subtitle={t('home.subtitle')}
        // No side-drawer/menu screen exists - "Open menu" goes to the
        // closest thing that already serves that purpose (account/settings).
        // The drawer, not Profile: Profile already has its own tab, and the
        // menu opening the same screen as the tab beside it was two ways to
        // one place. See AppDrawer.
        onMenuClick={drawer.open}
        // Real notification history - see the Notifications screen.
        onBellClick={() => navigate('/notifications')}
        unreadCount={unreadCount}
      />

      {push.shouldPrompt && (
        <PushPromptCard audience="rider" busy={push.busy} onTurnOn={push.turnOn} onDismiss={push.dismiss} />
      )}

      {/* Said up front rather than after she has chosen a pickup. A
          notice, not a block: the trip's pickup and drop are what decide
          whether it can be booked, not where her phone is. */}
      <OutOfAreaBanner />

      {/* An unpaid trip blocks the next booking - say so before she tries. */}
      <UnpaidTripBanner />

      {/* The mockup puts a woman-in-helmet graphic on the banner's right.
          Reusing the existing brand illustration (a woman in a helmet on a
          scooter) rather than adding a second asset for one banner - it is
          cropped by the card's overflow so the rider fills the corner. */}
      <Card variant="primary" className="relative overflow-hidden">
        <div className="relative z-10 max-w-[62%]">
          <p className="font-heading text-lg font-semibold">{fromContent('home.banner.title', 'home.bannerTitle')}</p>
          <p className="mt-1 text-sm opacity-90">{fromContent('home.banner.subtitle', 'home.bannerSubtitle')}</p>
        </div>
        <img
          src={brandIllustration}
          alt=""
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-3 -right-3 h-28 w-28 object-contain opacity-95"
        />
      </Card>

      <div>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('home.services')}</h2>
        {/* Two equal service tiles keep the artwork, label, and action aligned
          as one compact option. Lunch Box is deferred for launch; the
          backend still accepts LUNCHBOX and its tile can return here. */}
        <div className="grid grid-cols-2 gap-4">
          {SERVICES.map((service) => (
            <button
              key={service.key}
              type="button"
              onClick={() => navigate(service.to)}
              className="flex min-w-0 flex-col items-center gap-1.5 text-center"
            >
              <span
                className={`flex aspect-square w-[92%] items-center justify-center overflow-hidden rounded-card p-1 text-text-inverse ${service.bg}`}
              >
                <img src={service.image} alt={service.alt} className="h-full w-full rounded-[inherit] object-contain" />
              </span>
              <span className="flex w-[92%] items-center justify-between gap-1 text-left">
                <span className="text-xs font-semibold leading-tight text-text-primary">{t(service.labelKey)}</span>
                <ArrowRight className="h-3.5 w-3.5 shrink-0 text-text-primary" aria-hidden="true" />
              </span>
            </button>
          ))}
        </div>
      </div>

      <Card tone="brand" className="relative flex items-center gap-3 overflow-hidden">
        <div className="flex-1">
          <p className="text-sm font-semibold text-primary">{fromContent('home.community.title', 'home.communityTitle')}</p>
          <p className="mt-1 text-xs text-text-secondary">{fromContent('home.community.subtitle', 'home.communitySubtitle')}</p>
        </div>
        <img src={womenImage} alt={t('home.threeWomen')} className="h-20 w-32 shrink-0 object-contain object-right" />
      </Card>

      <div>
        {/* Four tiles, four destinations. Live Track and History used to
            both open /bookings, which is where the Bookings tab goes too -
            so three of the app's entry points showed one identical list and
            two of them earned their place on the screen by doing nothing.
            They now open the same screen scoped to genuinely different
            questions: what is happening now, and what already happened. */}
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('home.quickAccess')}</h2>
        <div className="flex justify-around">
          <ListRow layout="stacked" icon={<IconCircle color="red" tone="soft" icon={<ShieldAlert />} />} label={t('home.sos')} onClick={() => navigate('/sos')} />
          <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<MapPinned />} />} label={t('home.liveTrack')} onClick={() => navigate('/bookings?view=live')} />
          <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<WalletIcon />} />} label={t('home.wallet')} onClick={() => navigate('/wallet')} />
          <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<Clock />} />} label={t('home.history')} onClick={() => navigate('/bookings?view=history')} />
        </div>
      </div>
    </div>
  );
}
