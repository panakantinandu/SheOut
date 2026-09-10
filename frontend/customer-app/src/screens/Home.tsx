import { Bike, Clock, MapPinned, Package, ShieldAlert, Wallet as WalletIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import { usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';

/** Real data: the greeting name comes from users' live GET /me. Everything else on this screen (banner copy, category icons, quick access) is static UI, same as the mockup - there's no "featured banner" or "quick access config" backend endpoint to fetch. */
export function Home() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);

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

      <Card variant="primary">
        <p className="font-heading text-lg font-semibold">Ride with confidence</p>
        <p className="mt-1 text-sm opacity-90">Safe rides, verified women partners</p>
      </Card>

      <div>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Services</h2>
        <div className="flex justify-around">
          <ListRow
            layout="stacked"
            icon={<IconCircle icon={<Bike />} />}
            label="Bike Taxi"
            onClick={() => navigate('/book/ride')}
          />
          <ListRow
            layout="stacked"
            icon={<IconCircle color="orange" icon={<Package />} />}
            label="Parcel Delivery"
            onClick={() => navigate('/book/parcel')}
          />
          {/* Lunch Box is deferred for launch - UI entry point only. The
              backend still accepts the LUNCHBOX category and this tile can
              come straight back with no server-side work. See
              DeliveryBooking for the matching route guard. */}
        </div>
      </div>

      <Card className="bg-primary-light">
        <p className="text-sm font-semibold text-primary">Women Supporting Women</p>
        <p className="mt-1 text-xs text-text-secondary">Safe &middot; Empowered &middot; Together</p>
      </Card>

      <div>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Quick Access</h2>
        <div className="flex justify-around">
          <ListRow layout="stacked" icon={<IconCircle color="red" tone="soft" icon={<ShieldAlert />} />} label="SOS" onClick={() => navigate('/sos')} />
          <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<MapPinned />} />} label="Live Track" onClick={() => navigate('/bookings')} />
          <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<WalletIcon />} />} label="Wallet" onClick={() => navigate('/wallet')} />
          <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<Clock />} />} label="History" onClick={() => navigate('/bookings')} />
        </div>
      </div>
    </div>
  );
}
