import { Home, User, Wallet, ClipboardList } from 'lucide-react';
import type { ReactNode } from 'react';
import { BottomNavBar } from '@sheout/design-system';
import { useLocation, useNavigate } from 'react-router-dom';

/**
 * Wraps every authenticated dashboard-level screen. No mockup was given
 * for the driver app (unlike customer-app's 10-screen brief) - this tab
 * set is derived from what the backend actually supports for a driver:
 * Home (online toggle + offers/active trip), Trips (history), Earnings
 * (computed from completed trips - see Earnings.tsx), Profile.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();

  const items = [
    { key: 'home', label: 'Home', icon: <Home />, path: '/home' },
    { key: 'trips', label: 'Trips', icon: <ClipboardList />, path: '/trips' },
    { key: 'earnings', label: 'Earnings', icon: <Wallet />, path: '/earnings' },
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
            active: location.pathname === item.path,
            onClick: () => navigate(item.path),
          }))}
        />
      </div>
    </div>
  );
}
