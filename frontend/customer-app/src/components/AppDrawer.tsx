import { Gift, LifeBuoy, Package, Settings, ShieldCheck, ShoppingBag, Store } from 'lucide-react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { ASSISTANT_NAME, Avatar, Drawer, phoneDisplay, useTranslation } from '@sheout/design-system';
import { usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useMyShop } from '../lib/useMyShop';

const APP_VERSION = '0.1.0';

interface DrawerControls {
  open: () => void;
}

const DrawerContext = createContext<DrawerControls>({ open: () => undefined });

/** Opens the menu drawer from anywhere below AppDrawerProvider. */
export function useAppDrawer(): DrawerControls {
  return useContext(DrawerContext);
}

/**
 * The rider app's menu: the things she does that are not a tab. The
 * Profile tab is the things about her (details, ID check, emergency
 * contacts, saved addresses); her money is the Wallet tab. Each destination
 * is in exactly one of those places - the menu used to repeat Profile entry
 * for entry, so Settings and My Products each had two ways in.
 * <p>
 * Three groups: safety and rewards; the Marketplace, where a seller also
 * finds her shop and products (a buyer, the way to start selling); and help
 * and Settings. The header shows who is signed in and opens Profile, as a
 * menu header does in most apps.
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
            ],
          },
          {
            key: 'marketplace',
            title: t('home.market.title'),
            items: [
              { key: 'marketplace', label: t('home.market.title'), sublabel: t('home.market.body'), icon: <ShoppingBag />, onClick: () => go('/seller') },
              ...(shop
                ? [
                    { key: 'shop', label: t('seller.myShop'), sublabel: t('seller.myShopSub'), icon: <Store />, onClick: () => go('/seller/manage') },
                    {
                      key: 'products',
                      label: t('seller.myProducts.title'),
                      sublabel: t('seller.myProducts.rowSub', { count: shop.products.length }),
                      icon: <Package />,
                      onClick: () => go('/seller/my-products'),
                    },
                  ]
                : [{ key: 'sell', label: t('drawer.sell'), icon: <Store />, onClick: () => go('/seller/manage') }]),
            ],
          },
          {
            key: 'help',
            title: t('drawer.helpAndInfo'),
            items: [
              { key: 'help', label: t('profile.help'), sublabel: t('profile.helpSub', { name: ASSISTANT_NAME }), icon: <LifeBuoy />, onClick: () => go('/help') },
              { key: 'settings', label: t('settings.title'), sublabel: t('settings.sub'), icon: <Settings />, onClick: () => go('/settings') },
            ],
          },
        ]}
        footer={t('drawer.version', { version: APP_VERSION })}
      />
    </DrawerContext.Provider>
  );
}
