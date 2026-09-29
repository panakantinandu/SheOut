import { ArrowRight, ChevronRight, LayoutGrid, Store } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ASSISTANT_NAME, PushPromptCard, TopHeader, contentText, useContentSection, useAppLanguage, usePushNotifications, useUnreadNotifications, bikeTaxiArt, parcelArt, marketplaceArt, womenArt } from '@sheout/design-system';
import { OutOfAreaBanner } from '../components/OutOfAreaBanner';
import { UnpaidTripBanner } from '../components/UnpaidTripBanner';
import { useAppDrawer } from '../components/AppDrawer';
import { Reveal } from '../components/Reveal';
import { RideHero } from '../components/home/RideHero';
import { CommunityCard } from '../components/home/CommunityCard';
import { CategoryMarquee } from '../components/home/CategoryMarquee';
import { QuickAccess } from '../components/home/QuickAccess';
import { AllServicesSheet } from '../components/home/AllServicesSheet';
import { AgentFab } from '../components/home/AgentFab';
import { SELLER_CATEGORIES } from '../lib/seller';
import { PUSH_TOKEN_KEY, contentApi, notificationsApi, pushApi, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useTranslation } from '@sheout/design-system';

/**
 * The two services Home is built around, as data so another is one entry,
 * not another copy of the card. Lunch Box is deferred for launch.
 * Marketplace is not here: it is a smaller card further down, so the first
 * screen reads as a ride app, not a shop.
 */
const SERVICES = [
  {
    key: 'ride',
    labelKey: 'home.serviceRide',
    bodyKey: 'home.serviceRideBody',
    to: '/book/ride',
    image: bikeTaxiArt,
    card: 'bg-primary-light',
    arrow: 'bg-primary',
  },
  {
    key: 'parcel',
    labelKey: 'home.serviceParcel',
    bodyKey: 'home.serviceParcelBody',
    to: '/book/parcel',
    image: parcelArt,
    card: 'bg-accent-orange-tint',
    arrow: 'bg-accent-orange',
  },
];

/**
 * Real data: the greeting name comes from users' live GET /me. The banner and
 * the community card are editable copy from the content module - operators
 * change them in the ops console - with the text they were seeded with as the
 * fallback. Service cards and quick access are still static UI.
 * <p>
 * Top to bottom it is a ride app: where are you going, the two services,
 * then the marketplace straight under them as one card (its categories
 * drifting past, a way in for sellers) - found with the other services,
 * not at the foot of the page - then what SheOut stands for (Women
 * Supporting Women) and the rider's own shortcuts (SOS, live trip, wallet,
 * history). "All services" beside the heading lists every service, and the
 * assistant waits in the bottom-right corner.
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
  const [allServices, setAllServices] = useState(false);
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

  const marqueeItems = SELLER_CATEGORIES.map((c) => ({ value: c.value, label: t(`seller.categories.${c.key}`), art: c.art, tint: c.tint }));

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

      <RideHero
        eyebrow={t('home.ride.eyebrow')}
        title={fromContent('home.banner.title', 'home.bannerTitle')}
        body={fromContent('home.banner.subtitle', 'home.bannerSubtitle')}
        whereTo={t('home.ride.whereTo')}
        art={bikeTaxiArt}
        onBook={() => navigate('/book/ride')}
      />

      <div>
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 className="font-heading text-section text-text-primary">{t('home.services')}</h2>
          {/* Home shows the two ride services; every service, Marketplace too, is one tap away here. */}
          <button
            type="button"
            onClick={() => setAllServices(true)}
            className="flex shrink-0 items-center gap-1.5 rounded-full bg-primary-light py-1.5 pl-2.5 pr-3 text-caption font-semibold text-primary transition-transform duration-100 motion-safe:active:scale-95"
            data-testid="home-all-services"
          >
            <LayoutGrid className="h-4 w-4" aria-hidden="true" />
            {t('home.allServices')}
          </button>
        </div>
        {/* Two big cards, ride first. Lunch Box is deferred for launch; the
            backend still accepts LUNCHBOX and its card can return here. */}
        <div className="grid grid-cols-2 gap-3" data-testid="home-services">
          {SERVICES.map((service, i) => (
            <button
              key={service.key}
              type="button"
              onClick={() => navigate(service.to)}
              // Arriving one after the other, just after the hero.
              style={{ animationDelay: `${160 + i * 90}ms` }}
              className={`group relative isolate flex min-w-0 flex-col overflow-hidden rounded-[1.5rem] ${service.card} p-3 pb-3.5 text-left shadow-lift transition-transform duration-100 motion-safe:animate-pop-in motion-safe:active:scale-[0.97]`}
              data-testid={`service-${service.key}`}
            >
              {/* A soft disc of light the picture stands on. */}
              <span aria-hidden="true" className="absolute left-1/2 top-4 -z-10 h-24 w-24 -translate-x-1/2 rounded-full bg-surface/70 blur-xl" />
              <span aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-0 -z-10 w-1/2 bg-gradient-to-r from-transparent via-white/30 to-transparent motion-safe:animate-sheen" style={{ animationDelay: `${1.2 + i * 1.4}s` }} />
              <img
                src={service.image}
                alt=""
                aria-hidden="true"
                draggable={false}
                className="mx-auto aspect-square w-[82%] select-none object-contain drop-shadow-[0_10px_14px_rgba(74,26,158,0.25)] transition-transform duration-300 ease-out group-hover:-translate-y-1 group-hover:scale-[1.03]"
              />
              {/* The arrow sits in the corner, over the picture, so the name
                  has the card's whole width and stays on one line. */}
              <span
                className={`absolute right-2.5 top-2.5 flex h-8 w-8 items-center justify-center rounded-full ${service.arrow} text-white shadow-lift transition-transform duration-200 group-hover:translate-x-0.5`}
                aria-hidden="true"
              >
                <ArrowRight className="h-4 w-4" strokeWidth={2.5} />
              </span>
              <span className="mt-2.5 block min-w-0">
                <span className="block whitespace-nowrap font-heading text-card-title leading-tight text-text-primary" data-testid={`service-${service.key}-label`}>
                  {t(service.labelKey)}
                </span>
                <span className="mt-0.5 block text-caption leading-snug text-text-secondary">{t(service.bodyKey)}</span>
              </span>
            </button>
          ))}
        </div>
      </div>

      {/* The marketplace, right under the two ride services and as one card: its categories drifting past - each
          opens that category - and, at its foot, the way in for sellers. */}
      <Reveal>
        <section className="overflow-hidden rounded-[1.75rem] border border-border bg-surface px-4 pb-3 pt-4 shadow-lift" data-testid="home-marketplace">
          <button
            type="button"
            onClick={() => navigate('/seller')}
            className="group flex w-full items-center gap-3 text-left"
            data-testid="service-seller"
          >
            <img
              src={marketplaceArt}
              alt=""
              aria-hidden="true"
              draggable={false}
              className="h-16 w-16 shrink-0 select-none object-contain drop-shadow-[0_8px_10px_rgba(190,24,93,0.25)] motion-safe:animate-bob"
            />
            <span className="min-w-0 flex-1">
              <span className="block font-heading text-card-title text-text-primary">{t('home.market.title')}</span>
              <span className="mt-0.5 block text-caption text-text-secondary">{t('home.market.body')}</span>
            </span>
            <ChevronRight className="h-5 w-5 shrink-0 text-text-secondary transition-transform duration-200 group-hover:translate-x-0.5" aria-hidden="true" />
          </button>
          <div className="mt-3">
            <CategoryMarquee items={marqueeItems} onPick={(value) => navigate(`/seller?cat=${value}`)} />
          </div>
          <button
            type="button"
            onClick={() => navigate('/seller/manage')}
            className="mt-2 flex items-center gap-1.5 py-1 text-caption font-semibold text-primary"
            data-testid="home-sell-link"
          >
            <Store className="h-4 w-4" aria-hidden="true" />
            {t('home.market.sell')}
            <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
          </button>
        </section>
      </Reveal>

      {/* What SheOut stands for, straight after what it does. */}
      <CommunityCard
        title={fromContent('home.community.title', 'home.communityTitle')}
        subtitle={fromContent('home.community.subtitle', 'home.communitySubtitle')}
        art={womenArt}
        artAlt={t('home.threeWomen')}
      />

      <Reveal>
        {/* Four tiles, four destinations. Live Track and History used to
            both open /bookings, which is where the Bookings tab goes too -
            so three of the app's entry points showed one identical list and
            two of them earned their place on the screen by doing nothing.
            They now open the same screen scoped to genuinely different
            questions: what is happening now, and what already happened. */}
        <QuickAccess
          title={t('home.quickAccess')}
          items={[
            { key: 'sos', label: t('home.sos'), sub: t('home.quick.sos'), onOpen: () => navigate('/sos') },
            { key: 'live', label: t('home.liveTrack'), sub: t('home.quick.live'), onOpen: () => navigate('/bookings?view=live') },
            { key: 'wallet', label: t('home.wallet'), sub: t('home.quick.wallet'), onOpen: () => navigate('/wallet') },
            { key: 'history', label: t('home.history'), sub: t('home.quick.history'), onOpen: () => navigate('/bookings?view=history') },
          ]}
        />
      </Reveal>

      {/* Room at the foot, so the assistant in the corner never sits on the last card. */}
      <div className="h-14" aria-hidden="true" />

      {/* The assistant, standing by in the corner, clear of SOS in the middle of the bar. */}
      <AgentFab
        lines={[
          t('home.agent.hello', { name: firstName || t('home.there') }),
          t('home.agent.line1'),
          t('home.agent.line2'),
        ]}
        label={t('home.agent.open', { name: ASSISTANT_NAME })}
        onOpen={() => navigate('/help/assistant')}
      />

      <AllServicesSheet
        open={allServices}
        onClose={() => setAllServices(false)}
        services={[
          ...SERVICES.map((service) => ({
            key: service.key,
            label: t(service.labelKey),
            body: t(service.bodyKey),
            art: service.image,
            tint: service.card,
            onOpen: () => navigate(service.to, { replace: true }),
          })),
          {
            key: 'marketplace',
            label: t('home.market.title'),
            body: t('home.market.body'),
            art: marketplaceArt,
            tint: 'bg-accent-red-tint',
            onOpen: () => navigate('/seller', { replace: true }),
          },
        ]}
      />
    </div>
  );
}
