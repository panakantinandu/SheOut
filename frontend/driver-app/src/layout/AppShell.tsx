import { ClipboardList, Home, User, Wallet } from 'lucide-react';
import type { ReactNode } from 'react';
import { BottomNavBar, PageTransition } from '@sheout/design-system';
import { useLocation, useNavigate } from 'react-router-dom';

/** Wraps every authenticated dashboard-level screen. Tab order/naming matches the mockup: Home/Earnings/Bookings/Profile. */
export function AppShell({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();

  const items = [
    { key: 'home', label: 'Home', icon: <Home />, path: '/home' },
    { key: 'earnings', label: 'Earnings', icon: <Wallet />, path: '/earnings' },
    { key: 'bookings', label: 'Bookings', icon: <ClipboardList />, path: '/bookings' },
    { key: 'profile', label: 'Profile', icon: <User />, path: '/profile' },
  ];

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col bg-background">
      {/* Screens fade and rise slightly as they arrive - see PageTransition.
          Keyed by path, so switching tabs is movement rather than a cut. */}
      <div className="flex-1 overflow-y-auto px-screen pb-28 pt-6">
        <PageTransition transitionKey={location.pathname}>{children}</PageTransition>
      </div>
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
