import { Bike, Clock, MapPinned, Package, ShieldAlert, UtensilsCrossed, Wallet as WalletIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import { usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { mockAction } from '../lib/mockAction';

/** Real data: the greeting name comes from users' live GET /me. Everything else on this screen (banner copy, category icons, quick access) is static UI, same as the mockup - there's no "featured banner" or "quick access config" backend endpoint to fetch. */
export function Home() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);

  useEffect(() => {
    usersApi.getMyProfile().then(setProfile).catch(() => setProfile(null));
  }, []);

  const firstName = profile?.name?.split(' ')[0];

  return (
    <div className="space-y-6">
      <TopHeader
        variant="greeting"
        title={`Hello, ${firstName || 'there'} 👋`}
        subtitle="Your safety, our priority"
        // No side-drawer/menu screen exists - "Open menu" goes to the
        // closest thing that already serves that purpose (account/settings).
        onMenuClick={() => navigate('/profile')}
        // No notifications module/screen exists - mock, not silently inert.
        onBellClick={() => mockAction('Notifications', 'no notifications module on the backend yet')}
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
          <ListRow
            layout="stacked"
            icon={<IconCircle color="green" icon={<UtensilsCrossed />} />}
            label="Lunch Box"
            onClick={() => navigate('/book/lunchbox')}
          />
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
