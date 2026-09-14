import { ArrowRight, Bike, Clock, MapPinned, Package, ShieldAlert, Wallet as WalletIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { brandIllustration, Card, IconCircle, ListRow, TopHeader, contentText, useContentSection } from '@sheout/design-system';
import { OutOfAreaBanner } from '../components/OutOfAreaBanner';
import { contentApi, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { ThreeWomen } from '../components/ThreeWomen';

/**
 * Two of the mockup's three service tiles - Lunch Box is deferred for
 * launch. Kept as data rather than repeated markup so restoring the third
 * is one entry, not another copy of the tile.
 */
const SERVICES = [
  { key: 'ride', label: 'Bike Taxi', to: '/book/ride', bg: 'bg-primary', icon: <Bike className="h-8 w-8" strokeWidth={1.5} /> },
  { key: 'parcel', label: 'Parcel Delivery', to: '/book/parcel', bg: 'bg-accent-orange', icon: <Package className="h-8 w-8" strokeWidth={1.5} /> },
];

/**
 * Real data: the greeting name comes from users' live GET /me. The banner and
 * the community card are editable copy from the content module - operators
 * change them in the ops console - with the text they were seeded with as the
 * fallback. Service tiles and quick access are still static UI.
 */
export function Home() {
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
  const greeting = profileLoading ? '' : `Hello, ${firstName || 'there'} 👋`;

  return (
    <div className="space-y-6">
      <TopHeader
        variant="greeting"
        title={greeting}
        subtitle="Your safety, our priority"
        // No side-drawer/menu screen exists - "Open menu" goes to the
        // closest thing that already serves that purpose (account/settings).
        onMenuClick={() => navigate('/profile')}
        // Real notification history - see the Notifications screen.
        onBellClick={() => navigate('/notifications')}
      />

      {/* Said up front rather than after she has chosen a pickup. A
          notice, not a block: the trip's pickup and drop are what decide
          whether it can be booked, not where her phone is. */}
      <OutOfAreaBanner />

      {/* The mockup puts a woman-in-helmet graphic on the banner's right.
          Reusing the existing brand illustration (a woman in a helmet on a
          scooter) rather than adding a second asset for one banner - it is
          cropped by the card's overflow so the rider fills the corner. */}
      <Card variant="primary" className="relative overflow-hidden">
        <div className="relative z-10 max-w-[62%]">
          <p className="font-heading text-lg font-semibold">{contentText(copy, 'home.banner.title', 'Ride with confidence')}</p>
          <p className="mt-1 text-sm opacity-90">{contentText(copy, 'home.banner.subtitle', 'Safe rides, verified women partners')}</p>
        </div>
        <img
          src={brandIllustration}
          alt=""
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-3 -right-3 h-28 w-28 object-contain opacity-95"
        />
      </Card>

      <div>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Services</h2>
        {/* Filled rounded-square tiles with a white line-art glyph and an
            arrow under the label, per the mockup - not the small tinted
            circles this used to render. Lunch Box is deferred for launch:
            the backend still accepts LUNCHBOX and its tile can come
            straight back here. See DeliveryBooking for the route guard. */}
        {/* Tiles keep the mockup's one-third width so the row still looks
            right when Lunch Box returns. Centred rather than left-aligned
            because at launch there are only two: a trailing empty third
            column reads as a missing tile, a centred pair reads as
            intentional. Remove justify-center when the third comes back. */}
        <div className="flex justify-center gap-3">
          {SERVICES.map((service) => (
            <button
              key={service.key}
              type="button"
              onClick={() => navigate(service.to)}
              className="flex w-[30%] flex-col items-center gap-2 text-center"
            >
              <span
                className={`flex aspect-square w-full items-center justify-center rounded-card text-text-inverse ${service.bg}`}
              >
                {service.icon}
              </span>
              <span className="text-xs font-semibold leading-tight text-text-primary">{service.label}</span>
              <ArrowRight className="h-3.5 w-3.5 text-text-primary" aria-hidden="true" />
            </button>
          ))}
        </div>
      </div>

      <Card tone="brand" className="relative flex items-center gap-3 overflow-hidden">
        <div className="flex-1">
          <p className="text-sm font-semibold text-primary">{contentText(copy, 'home.community.title', 'Women Supporting Women')}</p>
          <p className="mt-1 text-xs text-text-secondary">{contentText(copy, 'home.community.subtitle', 'Safe · Empowered · Together')}</p>
        </div>
        <ThreeWomen className="h-16 w-24 shrink-0" />
      </Card>

      <div>
        {/* Four tiles, four destinations. Live Track and History used to
            both open /bookings, which is where the Bookings tab goes too -
            so three of the app's entry points showed one identical list and
            two of them earned their place on the screen by doing nothing.
            They now open the same screen scoped to genuinely different
            questions: what is happening now, and what already happened. */}
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Quick Access</h2>
        <div className="flex justify-around">
          <ListRow layout="stacked" icon={<IconCircle color="red" tone="soft" icon={<ShieldAlert />} />} label="SOS" onClick={() => navigate('/sos')} />
          <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<MapPinned />} />} label="Live Track" onClick={() => navigate('/bookings?view=live')} />
          <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<WalletIcon />} />} label="Wallet" onClick={() => navigate('/wallet')} />
          <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<Clock />} />} label="History" onClick={() => navigate('/bookings?view=history')} />
        </div>
      </div>
    </div>
  );
}
