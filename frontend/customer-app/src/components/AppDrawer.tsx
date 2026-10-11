import { Gift, LifeBuoy, Package, Settings, ShieldCheck, ShoppingBag, Store } from 'lucide-react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar, Drawer, phoneDisplay, useTranslation } from '@sheout/design-system';
import { usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useMyShop } from '../lib/useMyShop';
import { APP_VERSION } from '../lib/appVersion';


interface DrawerControls {
  open: () => void;
}

const DrawerContext = createContext<DrawerControls>({ open: () => undefined });

/** Opens the menu drawer from anywhere below AppDrawerProvider. */
export function useAppDrawer(): DrawerControls {
  return useContext(DrawerContext);
}

/**
 * The rider app's menu: the things she does that are not a tab - Safety
 * Center, Refer a Friend, the Marketplace with her shop and products (or the
 * way to start selling), then Help & support and Settings. The Profile tab
 * is the things about her (details, places, payments, ID check, emergency
 * contacts), and nothing is in both: the menu used to repeat Profile entry
 * for entry, so Settings and My Products each had two ways in. Plain names,
 * no line under each. The header shows who is signed in and opens Profile.
 */
export function AppDrawerProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const shop = useMyShop(isOpen && isAuthenticated);

  useEffect(() => {
    if (!isOpen || !isAuthenticated) return;
    usersApi.getMyProfile().then(setProfile).catch(() => undefined);
  }, [isOpen, isAuthenticated]);

  const open = useCallback(() => setIsOpen(true), []);
  const close = useCallback(() => setIsOpen(false), []);
  const go = (path: string) => {
    setIsOpen(false);
    navigate(path);
  };

  const controls = useMemo(() => ({ open }), [open]);

  return (
    <DrawerContext.Provider value={controls}>
      {children}
      <Drawer
        open={isOpen}
        onClose={close}
        header={
          <button
            type="button"
            onClick={() => go('/profile')}
            className="flex w-full items-center gap-3 text-left"
            aria-label={profile ? undefined : t('profile.title')}
            data-testid="drawer-profile"
          >
            <Avatar url={profile?.profilePhotoUrl} name={profile?.name} size="lg" />
            {/* Blank until her profile has loaded, rather than "Add your name" flashing before her name. */}
            <div className="min-w-0">
              <p className="truncate font-heading text-section">{profile ? profile.name || t('profile.addName') : ''}</p>
              <p className="truncate text-sm opacity-85">{phoneDisplay(profile?.phoneNumber)}</p>
            </div>
          </button>
        }
        sections={[
          {
            key: 'main',
            items: [
              { key: 'safety', label: t('drawer.safety'), icon: <ShieldCheck />, onClick: () => go('/safety') },
              { key: 'refer', label: t('drawer.refer'), icon: <Gift />, onClick: () => go('/refer') },
              { key: 'marketplace', label: t('home.market.title'), icon: <ShoppingBag />, onClick: () => go('/seller') },
              ...(shop
                ? [
                    { key: 'shop', label: t('seller.myShop'), icon: <Store />, onClick: () => go('/seller/manage') },
                    { key: 'products', label: t('seller.myProducts.title'), icon: <Package />, onClick: () => go('/seller/my-products') },
                  ]
                : [{ key: 'sell', label: t('drawer.sell'), icon: <Store />, onClick: () => go('/seller/manage') }]),
            ],
          },
          {
            key: 'help',
            title: t('drawer.helpAndInfo'),
            items: [
              { key: 'help', label: t('profile.help'), icon: <LifeBuoy />, onClick: () => go('/help') },
              { key: 'settings', label: t('settings.title'), icon: <Settings />, onClick: () => go('/settings') },
            ],
          },
        ]}
        footer={t('drawer.version', { version: APP_VERSION })}
      />
    </DrawerContext.Provider>
  );
}
