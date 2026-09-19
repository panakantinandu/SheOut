import { Calendar, Home, Siren, User, Wallet } from 'lucide-react';
import type { ReactNode } from 'react';
import { BottomNavBar, PageTransition } from '@sheout/design-system';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from '@sheout/design-system';

/**
 * Wraps every authenticated screen that shows the bottom nav (Home,
 * Bookings, Wallet, Profile - SOS is the raised item, matching the
 * mockup's footer on those four screens). Booking-flow and tracking
 * screens are pushed on top without this shell, same as the mockup shows
 * a back-arrow header instead of the tab bar on those.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  const items = [
    { key: 'home', label: t('nav.home'), icon: <Home />, path: '/home' },
    { key: 'bookings', label: t('nav.bookings'), icon: <Calendar />, path: '/bookings' },
    { key: 'sos', label: t('nav.sos'), icon: <Siren />, path: '/sos', raised: true },
    { key: 'wallet', label: t('nav.wallet'), icon: <Wallet />, path: '/wallet' },
    { key: 'profile', label: t('nav.profile'), icon: <User />, path: '/profile' },
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
            raised: item.raised,
            active: location.pathname === item.path,
            onClick: () => navigate(item.path),
          }))}
        />
      </div>
    </div>
  );
}
