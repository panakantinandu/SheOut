import { Calendar, Home, Siren, User, Wallet } from 'lucide-react';
import type { ReactNode } from 'react';
import { BottomNavBar } from '@sheout/design-system';
import { useLocation, useNavigate } from 'react-router-dom';

/**
 * Wraps every authenticated screen that shows the bottom nav (Home,
 * Bookings, Wallet, Profile - SOS is the raised item, matching the
 * mockup's footer on those four screens). Booking-flow and tracking
 * screens are pushed on top without this shell, same as the mockup shows
 * a back-arrow header instead of the tab bar on those.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();

  const items = [
    { key: 'home', label: 'Home', icon: <Home />, path: '/home' },
    { key: 'bookings', label: 'Bookings', icon: <Calendar />, path: '/bookings' },
    { key: 'sos', label: 'SOS', icon: <Siren />, path: '/sos', raised: true },
    { key: 'wallet', label: 'Wallet', icon: <Wallet />, path: '/wallet' },
    { key: 'profile', label: 'Profile', icon: <User />, path: '/profile' },
  ];

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col bg-background">
      <div className="flex-1 overflow-y-auto px-screen pb-28 pt-6">{children}</div>
      <div className="fixed inset-x-0 bottom-0 mx-auto max-w-md">
        <BottomNavBar
          items={items.map((item) => ({
            key: item.key,
            label: item.label,
            icon: item.icon,
            raised: item.raised,
            active: location.pathname === item.path,
            onClick: () => navigate(item.path),
          }))}
        />
      </div>
    </div>
  );
}
