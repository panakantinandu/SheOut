import { Gift, LifeBuoy, Settings, ShieldCheck } from 'lucide-react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar, Drawer, phoneDisplay, useTranslation } from '@sheout/design-system';
import { usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';

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
 * The rider app's menu: the app-wide places - Safety Center, Refer a
 * Friend, Help & support and Settings - and nothing that is on a tab.
 * <p>
 * Each place is in one spot only. What is about her (details, ID, emergency
 * contacts, addresses, her shop) is on the Profile tab; her money is the
 * Wallet tab. The menu used to repeat Profile entry for entry, so Settings
 * and My Products each had two ways in. The header shows who is signed in
 * and opens Profile, as a menu header does in most apps.
 */
export function AppDrawerProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);

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
